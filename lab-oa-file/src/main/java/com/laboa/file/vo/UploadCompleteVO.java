package com.laboa.file.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadCompleteVO {

    /** 文件ID */
    private Long fileId;

    /** 文件名 */
    private String fileName;

    /** MinIO 存储路径 */
    private String minioPath;

    /** 文件大小 */
    private long fileSize;

    /** 合并状态 */
    private String status;

    /** 解析出的正文（截取前 2000 字符用于前端预览） */
    private String parsedText;

    /** 文件完整正文长度 */
    private int textLength;

    /** 解析耗时 (ms) */
    private long parseTimeMs;
}