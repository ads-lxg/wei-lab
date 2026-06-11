package com.laboa.rag.service;

import java.util.List;
import java.util.Map;

/**
 * 对话记忆服务接口
 * 管理短期记忆（最近10轮）、上下文压缩、历史持久化
 */
public interface MemoryService {

    /**
     * 获取指定会话的对话历史（用于构建prompt）
     * @param sessionId 会话ID
     * @return 消息列表 [{role, content}]
     */
    List<Map<String, String>> getHistory(String sessionId);

    /**
     * 添加一条消息到对话历史
     * @param sessionId 会话ID
     * @param role 角色 (user/assistant/system)
     * @param content 消息内容
     * @param citationsJson 引用来源JSON（可为null）
     */
    void addMessage(String sessionId, String role, String content, String citationsJson);

    /**
     * 估算当前对话的token数（按字符数/4粗略估算）
     * @param sessionId 会话ID
     * @param contextText 检索上下文文本
     * @param systemPrompt 系统提示词
     * @return 估算的token数
     */
    int estimateTokens(String sessionId, String contextText, String systemPrompt);

    /**
     * 压缩对话历史：当token数超过阈值时，对最早的对话生成摘要
     * @param sessionId 会话ID
     */
    void compressIfNeeded(String sessionId);
}
