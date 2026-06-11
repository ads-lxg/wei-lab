package com.laboa.rag.service;

import reactor.core.publisher.Flux;

/**
 * 大语言模型服务接口 - DeepSeek API
 */
public interface LLMService {

    /**
     * 流式对话，返回 SSE 事件流
     * @param messages 消息列表，每条为 Map(role, content)
     * @return SSE 事件流 (每个事件包含 delta 文本)
     */
    Flux<String> streamChat(java.util.List<java.util.Map<String, String>> messages);

    /**
     * 非流式对话（用于上下文压缩摘要生成）
     * @param messages 消息列表
     * @return 完整回复文本
     */
    String chat(java.util.List<java.util.Map<String, String>> messages);
}
