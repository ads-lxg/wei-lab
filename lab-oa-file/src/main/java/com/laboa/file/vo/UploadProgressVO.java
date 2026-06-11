package com.laboa.file.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadProgressVO {

    /** 总片数 */
    private int totalChunks;

    /** 每片大小 (byte) */
    private long chunkSize;

    /** 文件大小 (byte) */
    private long fileSize;

    /** 已上传片数 */
    private int uploadedCount;

    /** 上传状态 */
    private String status;

    /** 已完成时的文件ID */
    private Long fileId;

    /** 已上传的分片索引（从1开始） */
    private List<Integer> uploadedIndexes;

    /** 缺失的分片索引（从1开始） */
    private List<Integer> missingIndexes;
}