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

    @Schema(description = "该目录下文献数量")
    private Long documentCount = 0L;

    @Schema(description = "该目录下的文献列表（仅包含基本信息）")
    private List<FolderDocumentVO> documents = new ArrayList<>();

    /**
     * 目录下文献简要信息
     */
    @Data
    @Schema(description = "目录下文献简要信息")
    public static class FolderDocumentVO {

        @Schema(description = "文献ID")
        private Long id;

        @Schema(description = "文件名")
        private String fileName;

        @Schema(description = "标题")
        private String title;

        @Schema(description = "文件类型")
        private String fileType;

        @Schema(description = "解析状态")
        private String parseStatus;
    }
}
