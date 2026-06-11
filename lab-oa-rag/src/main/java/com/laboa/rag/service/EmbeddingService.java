package com.laboa.rag.service;

import java.util.List;

/**
 * 向量嵌入服务接口 - 调用阿里 Embedding API
 */
public interface EmbeddingService {

    /**
     * 对单个文本生成嵌入向量
     * @param text 输入文本
     * @return 2048维浮点数组
     */
    float[] embed(String text);

    /**
     * 批量生成嵌入向量
     * @param texts 输入文本列表
     * @return 嵌入向量列表
     */
    List<float[]> embedBatch(List<String> texts);
}
