package com.laboa.literature.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文献详情VO
 */
@Data
@Schema(description = "文献详情")
public class LiteratureDetailVO {

    @Schema(description = "文献ID")
    private Long id;

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

    @Schema(description = "发表日期")
    private LocalDate publishDate;

    @Schema(description = "来源期刊")
    private String sourceJournal;

    @Schema(description = "DOI号")
    private String doi;

    @Schema(description = "所属目录ID")
    private Long folderId;

    @Schema(description = "所属目录名称")
    private String folderName;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "上传人ID")
    private Long uploaderId;

    @Schema(description = "上传人姓名")
    private String uploaderName;

    @Schema(description = "上传时间")
    private LocalDateTime createTime;

    @Schema(description = "下载次数")
    private Integer downloadCount;

    @Schema(description = "浏览次数")
    private Integer viewCount;

    @Schema(description = "是否RAG来源")
    private Integer ragSource;

    @Schema(description = "解析状态")
    private String parseStatus;
}
