package com.laboa.literature.util;

import com.laboa.literature.entity.RagFolder;
import com.laboa.literature.vo.FolderTreeVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 目录树构建工具类
 * <p>
 * 使用高性能 Map 方式一次构建完整树结构，避免多层递归数据库查询。
 * 时间复杂度 O(n)，适用于无限级目录树。
 */
public final class FolderTreeBuilder {

    private FolderTreeBuilder() {
        // 工具类禁止实例化
    }

    /**
     * 根据数据库中的扁平目录列表构建树形结构
     *
     * @param folders 一次查询的全部目录列表（需包含根及所有后代节点）
     * @return 树形结构的根节点列表
     */
    public static List<FolderTreeVO> build(List<RagFolder> folders) {
        if (folders == null || folders.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 转为 VO 并放入 Map，key=id，方便 O(1) 查找
        Map<Long, FolderTreeVO> nodeMap = new LinkedHashMap<>();
        for (RagFolder folder : folders) {
            FolderTreeVO vo = toVO(folder);
            nodeMap.put(vo.getId(), vo);
        }

        // 2. 遍历构建父子关系
        List<FolderTreeVO> roots = new ArrayList<>();
        for (FolderTreeVO vo : nodeMap.values()) {
            Long parentId = vo.getParentId();
            if (parentId == null || parentId == 0) {
                // 根节点
                roots.add(vo);
            } else {
                FolderTreeVO parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // 父节点不在列表中（已删除等情况），当作根节点处理
                    roots.add(vo);
                }
            }
        }

        // 3. 按 sortOrder 排序（每层内部排序）
        sortChildren(roots);

        return roots;
    }

    /**
     * 递归对每层子节点按 sortOrder 排序
     */
    private static void sortChildren(List<FolderTreeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparing(FolderTreeVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FolderTreeVO::getId));
        for (FolderTreeVO node : nodes) {
            sortChildren(node.getChildren());
        }
    }

    /**
     * Entity 转 VO
     */
    private static FolderTreeVO toVO(RagFolder folder) {
        FolderTreeVO vo = new FolderTreeVO();
        vo.setId(folder.getId());
        vo.setParentId(folder.getParentId());
        vo.setFolderName(folder.getFolderName());
        vo.setPath(folder.getPath());
        vo.setLevelNo(folder.getLevelNo());
        vo.setSortOrder(folder.getSortOrder());
        return vo;
    }
}
