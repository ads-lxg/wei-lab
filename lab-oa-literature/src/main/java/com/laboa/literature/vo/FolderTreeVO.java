package com.laboa.literature.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录树VO，用于前端Tree组件展示
 */
@Data
@Schema(description = "目录树节点")
public class FolderTreeVO {

    @Schema(description = "目录ID")
    private Long id;

    @Schema(description = "父目录ID")
    private Long parentId;

    @Schema(description = "目录名称")
    private String folderName;

    @Schema(description = "目录路径")
    private String path;

    @Schema(description = "目录层级")
    private Integer levelNo;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "子目录列表")
    private List<FolderTreeVO> children = new ArrayList<>();
}
