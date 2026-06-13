package com.laboa.literature.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站文献VO
 */
@Data
@Schema(description = "回收站文献")
public class LiteratureRecycleVO {

    @Schema(description = "文献ID")
    private Long id;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "作者")
    private String authors;

    @Schema(description = "所属目录ID")
    private Long folderId;

    @Schema(description = "所属目录名称")
    private String folderName;

    @Schema(description = "删除时间")
    private LocalDateTime deleteTime;

    @Schema(description = "上传时间")
    private LocalDateTime createTime;
}
