package com.laboa.rag.service;

/**
 * 文档入库服务接口 - 文档加载、分割、向量化、存入ES
 */
public interface DocumentIngestionService {

    /**
     * 从文本内容入库（Tika解析后的正文）
     * @param content 文本内容
     * @param fileName 文件名
     * @param sourcePath 来源路径
     * @param docType 文档类型 (literature / doc)
     * @param docId 文档ID
     */
    void ingestText(String content, String fileName, String sourcePath, String docType, Long docId);

    /**
     * 从本地文件加载并入库
     * @param filePath 本地文件路径
     * @param docType 文档类型
     * @param docId 文档ID
     */
    void ingestFile(String filePath, String docType, Long docId);
}
