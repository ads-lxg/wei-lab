package com.laboa.rag.service;

import com.laboa.rag.dto.Citation;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * RAG 聊天服务接口 - 编排完整的 RAG 管道
 */
public interface ChatService {

    /**
     * 流式 RAG 对话
     */
    Flux<ChatEvent> streamChat(String sessionId, Long userId, String message);

    /**
     * 创建新会话
     */
    String createSession(Long userId);

    /**
     * 获取会话历史消息
     */
    List<ChatMessageVO> getMessages(String sessionId, int page, int size);

    /**
     * 搜索会话（按标题或消息内容模糊匹配）
     * @param userId 用户ID
     * @param keyword 搜索关键词
     * @return 匹配的会话列表
     */
    List<SessionVO> searchSessions(Long userId, String keyword);

    /**
     * 导出会话
     * @param sessionId 会话ID
     * @param format 导出格式: json / markdown
     * @return 导出内容
     */
    String exportSession(String sessionId, String format);

    /**
     * 修改用户消息并重新生成助手回复
     * 删除该消息之后的所有消息，修改该消息内容，重新触发RAG对话
     * @param sessionId 会话ID
     * @param messageId 要修改的消息ID
     * @param newContent 新的消息内容
     * @param userId 用户ID
     * @return SSE 事件流
     */
    Flux<ChatEvent> regenerateFromMessage(String sessionId, Long messageId, String newContent, Long userId);

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
    record ChatMessageVO(Long id, String role, String content, String citationsJson, String createTime) {}

    /**
     * 会话VO（搜索结果）
     */
    record SessionVO(String sessionId, String title, String createTime, Long messageCount) {}
}
