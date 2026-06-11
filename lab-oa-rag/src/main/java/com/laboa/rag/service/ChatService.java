package com.laboa.rag.service;

import com.laboa.rag.dto.Citation;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 聊天服务接口 - 编排完整的 RAG 管道
 */
public interface ChatService {

    /**
     * 流式 RAG 对话
     * @param sessionId 会话ID（可为null，自动创建）
     * @param userId 用户ID
     * @param message 用户消息
     * @return SSE 事件流 (content delta + 最终 citation 事件)
     */
    Flux<ChatEvent> streamChat(String sessionId, Long userId, String message);

    /**
     * 创建新会话
     * @param userId 用户ID
     * @return 新会话ID
     */
    String createSession(Long userId);

    /**
     * 获取会话历史消息
     * @param sessionId 会话ID
     * @param page 页码
     * @param size 每页条数
     * @return 消息列表
     */
    List<ChatMessageVO> getMessages(String sessionId, int page, int size);

    /**
     * SSE 事件类型
     */
    record ChatEvent(String type, String content, List<Citation> citations, String sessionId, boolean ragAvailable) {
        public static ChatEvent delta(String content) {
            return new ChatEvent("delta", content, null, null, false);
        }
        public static ChatEvent done(List<Citation> citations, String sessionId, boolean ragAvailable) {
            return new ChatEvent("done", null, citations, sessionId, ragAvailable);
        }
    }

    /**
     * 消息VO
     */
    record ChatMessageVO(String role, String content, String citationsJson, String createTime) {}
}
