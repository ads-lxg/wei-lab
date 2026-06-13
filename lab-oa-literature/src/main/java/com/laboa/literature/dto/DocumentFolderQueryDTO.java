package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 目录下文献查询DTO
 */
@Data
@Schema(description = "目录下文献查询请求")
public class DocumentFolderQueryDTO {

    @Schema(description = "目录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long folderId;

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer size = 10;

    @Schema(description = "搜索关键词（模糊匹配文件名、标题、作者、关键词）")
    private String keyword;

    @Schema(description = "排序字段: createTime / downloadCount", example = "createTime")
    private String sortField = "createTime";

    @Schema(description = "排序方向: asc / desc", example = "desc")
    private String sortOrder = "desc";
}
