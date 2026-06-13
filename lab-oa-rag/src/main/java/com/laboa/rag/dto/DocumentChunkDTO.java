package com.laboa.rag.dto;

import lombok.Data;
import java.util.Map;

/**
 * 文档分块DTO，用于ES存储和检索
 */
@Data
public class DocumentChunkDTO {
    /** 文档ID (ES doc _id) */
    private String id;

    /** chunk文本内容 */
    private String content;

    /** 2048维向量 */
    private float[] embedding;

    /** 元数据 */
    private String fileName;

    private String sourcePath;

    private int chunkIndex;

    private String docType;

    private String docId;

    /** 检索得分 */
    private double score;
}
