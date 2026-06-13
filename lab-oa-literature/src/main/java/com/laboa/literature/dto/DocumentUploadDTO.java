package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 文献上传DTO
 */
@Data
@Schema(description = "文献上传请求")
public class DocumentUploadDTO {

    @Schema(description = "所属目录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属目录ID不能为空")
    private Long folderId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "作者")
    private String authors;

    @Schema(description = "关键词")
    private String keywords;

    @Schema(description = "摘要")
    private String abstractText;

    @Schema(description = "发表日期")
    private LocalDate publishDate;

    @Schema(description = "来源期刊")
    private String sourceJournal;

    @Schema(description = "DOI号")
    private String doi;

    @Schema(description = "是否作为RAG来源: 0=否, 1=是")
    private Integer ragSource = 0;
}
