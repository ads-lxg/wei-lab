package com.laboa.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文献/文档引用出处
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Citation {
    /** 引用编号 [1], [2]... */
    private int referenceNumber;

    /** 文件名 */
    private String fileName;

    /** 来源路径 */
    private String sourcePath;

    /** chunk序号 */
    private int chunkIndex;

    /** 摘录片段 */
    private String excerpt;
}
