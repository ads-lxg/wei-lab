package com.laboa.rag.service;

import com.laboa.rag.dto.DocumentChunkDTO;
import java.util.List;

/**
 * 向量存储服务接口 - ES doc_chunks 索引
 */
public interface VectorStoreService {

    /**
     * 创建 doc_chunks 索引（含 dense_vector mapping）
     */
    void createIndexIfNotExists();

    /**
     * 存储文档分块（含向量）到 ES
     * @param chunk 文档分块DTO
     */
    void storeChunk(DocumentChunkDTO chunk);

    /**
     * 批量存储文档分块
     * @param chunks 分块列表
     */
    void storeChunks(List<DocumentChunkDTO> chunks);

    /**
     * 删除指定文档的所有分块
     * @param docType 文档类型 (literature / doc)
     * @param docId 文档ID
     */
    void deleteByDocId(String docType, Long docId);

    /**
     * 扫描 doc_chunks 索引中指定 docType 的所有 docId
     * 用于补偿清理：检查 MySQL 中是否仍存在对应记录
     */
    List<Long> findAllDocIds(String docType);
}
