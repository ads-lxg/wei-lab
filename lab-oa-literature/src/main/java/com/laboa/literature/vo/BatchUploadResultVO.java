package com.laboa.literature.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量上传结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量上传结果")
public class BatchUploadResultVO {

    @Schema(description = "成功数量")
    private int successCount;

    @Schema(description = "失败数量")
    private int failCount;

    @Schema(description = "失败列表")
    @Builder.Default
    private List<FailItem> failList = new ArrayList<>();

    @Schema(description = "成功上传的文献ID列表（用于前端轮询解析状态）")
    @Builder.Default
    private List<Long> successIds = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "失败项")
    public static class FailItem {

        @Schema(description = "文件名")
        private String fileName;

        @Schema(description = "标题")
        private String title;

        @Schema(description = "作者")
        private String authors;

        @Schema(description = "关键词")
        private String keywords;

        @Schema(description = "摘要")
        private String abstractText;

        @Schema(description = "发表时间")
        private String publishDate;

        @Schema(description = "来源期刊")
        private String sourceJournal;

        @Schema(description = "DOI")
        private String doi;

        @Schema(description = "RAG来源")
        private Integer ragSource;

        @Schema(description = "失败原因")
        private String reason;
    }
}
