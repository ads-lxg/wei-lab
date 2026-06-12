package com.laboa.literature.service;

import com.laboa.literature.dto.FolderCreateDTO;
import com.laboa.literature.dto.FolderMoveDTO;
import com.laboa.literature.dto.FolderUpdateDTO;
import com.laboa.literature.vo.FolderTreeVO;

import java.util.List;

/**
 * 文献目录服务接口
 */
public interface RagFolderService {

    /**
     * 查询完整目录树
     */
    List<FolderTreeVO> tree();

    /**
     * 创建目录
     * @return 新目录ID
     */
    Long create(FolderCreateDTO dto);

    /**
     * 修改目录名称
     */
    void update(FolderUpdateDTO dto);

    /**
     * 删除目录（逻辑删除）
     */
    void delete(Long id);

    /**
     * 移动目录到新的父节点下
     */
    void move(FolderMoveDTO dto);
}
