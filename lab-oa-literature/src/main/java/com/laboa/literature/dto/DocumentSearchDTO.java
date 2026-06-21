package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文献搜索DTO
 */
@Data
@Schema(description = "文献搜索请求")
public class DocumentSearchDTO {

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer size = 10;

    @Schema(description = "搜索关键词（模糊匹配文件名、标题、作者、关键词、摘要）")
    private String keyword;

    @Schema(description = "排序字段: createTime / downloadCount", example = "createTime")
    private String sortField = "createTime";

    @Schema(description = "排序方向: asc / desc", example = "desc")
    private String sortOrder = "desc";

    @Schema(description = "搜索模式: bm25=关键词精确匹配优先, knn=语义检索优先(支持中英跨语言), hybrid=均衡混合", example = "hybrid")
    private String searchMode = "hybrid";
}
