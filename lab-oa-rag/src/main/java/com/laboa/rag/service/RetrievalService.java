package com.laboa.rag.service;

import com.laboa.rag.dto.DocumentChunkDTO;
import java.util.List;

/**
 * 检索服务接口 - k-NN 向量相似度搜索
 */
public interface RetrievalService {

    /**
     * 根据查询文本检索相关文档片段
     * @param query 查询文本
     * @param topK 返回前K个结果，默认5
     * @return 相关文档片段列表
     */
    List<DocumentChunkDTO> retrieve(String query, int topK);

    /**
     * 混合检索：向量 + BM25 关键词
     * @param query 查询文本
     * @param topK 返回前K个结果
     * @return 相关文档片段列表
     */
    List<DocumentChunkDTO> hybridRetrieve(String query, int topK);
}
