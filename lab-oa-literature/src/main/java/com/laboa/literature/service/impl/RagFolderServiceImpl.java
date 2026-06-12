package com.laboa.literature.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.exception.ErrorCode;
import com.laboa.literature.dto.FolderCreateDTO;
import com.laboa.literature.dto.FolderMoveDTO;
import com.laboa.literature.dto.FolderUpdateDTO;
import com.laboa.literature.entity.RagFolder;
import com.laboa.literature.mapper.RagFolderMapper;
import com.laboa.literature.service.RagFolderService;
import com.laboa.literature.util.FolderTreeBuilder;
import com.laboa.literature.vo.FolderTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文献目录服务实现
 * <p>
 * 采用 ParentId + Path 模式管理无限级目录树。
 * 关键设计：
 * - 创建时自动生成 path 和 level_no
 * - 移动时递归更新所有子节点的 path 和 level_no
 * - 所有写操作均在事务中执行，保证树数据一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagFolderServiceImpl implements RagFolderService {

    private final RagFolderMapper ragFolderMapper;

    // ==================== 查询目录树 ====================

    @Override
    public List<FolderTreeVO> tree() {
        // 一次查询全部目录，内存中构建树
        List<RagFolder> allFolders = ragFolderMapper.selectList(
                new LambdaQueryWrapper<RagFolder>()
                        .orderByAsc(RagFolder::getSortOrder)
                        .orderByAsc(RagFolder::getId)
        );
        return FolderTreeBuilder.build(allFolders);
    }

    // ==================== 创建目录 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FolderCreateDTO dto) {
        // 1. 校验父目录存在（parentId=0 表示根目录，无需校验）
        RagFolder parent = null;
        if (dto.getParentId() != 0) {
            parent = ragFolderMapper.selectById(dto.getParentId());
            if (parent == null) {
                throw new BusinessException(ErrorCode.FOLDER_PARENT_NOT_EXIST);
            }
        }

        // 2. 校验同级目录名称不重复
        checkNameUnique(dto.getParentId(), dto.getFolderName(), null);

        // 3. 生成 path 和 level_no
        String path;
        int levelNo;
        if (parent == null) {
            // 根节点：path 暂时用占位符，插入后回填
            path = "/TMP"; // 临时值，插入后更新
            levelNo = 1;
        } else {
            path = parent.getPath(); // 父路径，插入后追加自身ID
            levelNo = parent.getLevelNo() + 1;
        }

        // 4. 插入目录
        RagFolder folder = new RagFolder();
        folder.setParentId(dto.getParentId());
        folder.setFolderName(dto.getFolderName());
        folder.setPath(path);
        folder.setLevelNo(levelNo);
        folder.setSortOrder(0);
        ragFolderMapper.insert(folder);

        // 5. 回填正确的 path（需要用到自增ID）
        String finalPath = (parent == null)
                ? "/" + folder.getId()
                : parent.getPath() + "/" + folder.getId();
        folder.setPath(finalPath);
        ragFolderMapper.updateById(folder);

        log.info("创建目录成功: id={}, name={}, path={}", folder.getId(), folder.getFolderName(), finalPath);
        return folder.getId();
    }

    // ==================== 修改目录名称 ====================

    @Override
    public void update(FolderUpdateDTO dto) {
        // 1. 校验目录存在
        RagFolder folder = ragFolderMapper.selectById(dto.getId());
        if (folder == null) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_EXIST);
        }

        // 2. 校验同级目录名称不重复（排除自身）
        checkNameUnique(folder.getParentId(), dto.getFolderName(), dto.getId());

        // 3. 更新名称
        folder.setFolderName(dto.getFolderName());
        ragFolderMapper.updateById(folder);

        log.info("修改目录名称成功: id={}, newName={}", dto.getId(), dto.getFolderName());
    }

    // ==================== 删除目录 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 1. 校验目录存在
        RagFolder folder = ragFolderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_EXIST);
        }

        // 2. 检查是否存在子目录
        Long childCount = ragFolderMapper.selectCount(
                new LambdaQueryWrapper<RagFolder>()
                        .eq(RagFolder::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.FOLDER_HAS_CHILDREN);
        }

        // 3. 逻辑删除（MyBatis Plus 自动处理 deleted 字段）
        ragFolderMapper.deleteById(id);

        log.info("删除目录成功: id={}, name={}", id, folder.getFolderName());
    }

    // ==================== 移动目录 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(FolderMoveDTO dto) {
        Long folderId = dto.getFolderId();
        Long targetParentId = dto.getTargetParentId();

        // 1. 校验要移动的目录存在
        RagFolder folder = ragFolderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_EXIST);
        }

        // 2. 校验目标父目录存在（0 表示根目录）
        RagFolder targetParent = null;
        if (targetParentId != 0) {
            targetParent = ragFolderMapper.selectById(targetParentId);
            if (targetParent == null) {
                throw new BusinessException(ErrorCode.FOLDER_MOVE_TARGET_NOT_EXIST);
            }
            // 3. 禁止移动到自身下面
            if (targetParentId.equals(folderId)) {
                throw new BusinessException(ErrorCode.FOLDER_MOVE_SELF);
            }
            // 4. 禁止移动到自己的子节点下面：
            //    检查目标父节点是否是当前节点的后代（目标父节点的 path 以当前节点 path 开头）
            if (targetParent.getPath().startsWith(folder.getPath() + "/")) {
                throw new BusinessException(ErrorCode.FOLDER_MOVE_TO_CHILD);
            }
        }

        // 5. 校验目标位置同级名称不重复
        checkNameUnique(targetParentId, folder.getFolderName(), folderId);

        // 6. 计算新旧 path
        String oldPath = folder.getPath();
        String newParentPath = (targetParent == null) ? "" : targetParent.getPath();
        int newLevelNo = (targetParent == null) ? 1 : targetParent.getLevelNo() + 1;
        int levelDelta = newLevelNo - folder.getLevelNo();

        // 7. 更新自身
        String newSelfPath = (targetParent == null)
                ? "/" + folderId
                : newParentPath + "/" + folderId;
        folder.setParentId(targetParentId);
        folder.setPath(newSelfPath);
        folder.setLevelNo(newLevelNo);
        ragFolderMapper.updateById(folder);

        // 8. 递归更新所有子节点的 path 和 level_no
        //    查询所有 path 以 oldPath/ 开头的子节点
        List<RagFolder> children = ragFolderMapper.selectList(
                new LambdaQueryWrapper<RagFolder>()
                        .likeRight(RagFolder::getPath, oldPath + "/")
        );

        for (RagFolder child : children) {
            // 将旧 path 前缀替换为新 path 前缀
            String newChildPath = child.getPath().replace(oldPath, newSelfPath);
            child.setPath(newChildPath);
            child.setLevelNo(child.getLevelNo() + levelDelta);
            ragFolderMapper.updateById(child);
        }

        log.info("移动目录成功: folderId={}, oldPath={} -> newPath={}, 更新子节点数={}",
                folderId, oldPath, newSelfPath, children.size());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验同级目录名称唯一
     *
     * @param parentId      父目录ID
     * @param folderName    目录名称
     * @param excludeId     排除的目录ID（修改时排除自身），null 表示不排除
     */
    private void checkNameUnique(Long parentId, String folderName, Long excludeId) {
        LambdaQueryWrapper<RagFolder> wrapper = new LambdaQueryWrapper<RagFolder>()
                .eq(RagFolder::getParentId, parentId)
                .eq(RagFolder::getFolderName, folderName);
        if (excludeId != null) {
            wrapper.ne(RagFolder::getId, excludeId);
        }
        Long count = ragFolderMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.FOLDER_NAME_REPEAT);
        }
    }
}
