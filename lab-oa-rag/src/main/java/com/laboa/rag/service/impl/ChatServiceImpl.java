package com.laboa.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboa.rag.dto.Citation;
import com.laboa.rag.dto.DocumentChunkDTO;
import com.laboa.rag.entity.ChatMessage;
import com.laboa.rag.entity.ChatSession;
import com.laboa.rag.mapper.ChatMessageMapper;
import com.laboa.rag.mapper.ChatSessionMapper;
import com.laboa.rag.service.ChatService;
import com.laboa.rag.service.MemoryService;
import com.laboa.rag.service.RetrievalService;
import com.laboa.rag.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 聊天服务实现 - 编排完整的 RAG 管道
 * 1. 检索 → 2. 上下文增强 → 3. 记忆管理 → 4. LLM流式生成 → 5. 标注出处 → 6. 持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final RetrievalService retrievalService;
    private final LLMService llmService;
    private final MemoryService memoryService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    @Value("${rag.system-prompt:你是一个实验室OA系统的智能助手。请基于以下参考资料和你的知识，全面、详细地回答用户的问题。参考资料中有相关内容时，请用[编号]标注出处；参考资料没有覆盖的部分，你可以结合自身知识进行补充和扩展。如果参考资料与问题完全无关，请直接根据你的知识回答。保持专业、详细的风格。}")
    private String ragSystemPrompt;

    @Value("${rag.pure-chat-prompt:你是一个实验室OA系统的智能助手。请用专业、简洁的风格回答用户的问题。}")
    private String pureChatPrompt;

    @Value("${rag.top-k:5}")
    private int topK;

    private static final String DEFAULT_SESSION_TITLE = "新对话";

    @Override
    public Flux<ChatEvent> streamChat(String sessionId, Long userId, String message) {
        // 1. 确保会话存在
        final String sid = (sessionId == null || sessionId.isBlank()) ? createSession(userId) : sessionId;

        // 2. 保存用户消息
        memoryService.addMessage(sid, "user", message, null);

        // 3. 尝试检索相关文档（失败则降级为纯对话）
        List<DocumentChunkDTO> relevantDocs = Collections.emptyList();
        boolean isRagAvailable = false;
        try {
            relevantDocs = retrievalService.hybridRetrieve(message, topK);
            isRagAvailable = !relevantDocs.isEmpty();
        } catch (Exception e) {
            log.warn("RAG检索失败，降级为纯LLM对话: {}", e.getMessage());
        }
        final boolean ragAvailable = isRagAvailable;

        // 4. 构建 citations（仅 RAG 模式）
        List<Citation> builtCitations = new ArrayList<>();
        if (ragAvailable) {
            for (int i = 0; i < relevantDocs.size(); i++) {
                DocumentChunkDTO doc = relevantDocs.get(i);
                String excerpt = doc.getContent();
                if (excerpt != null && excerpt.length() > 200) {
                    excerpt = excerpt.substring(0, 200) + "...";
                }
                builtCitations.add(new Citation(i + 1, doc.getFileName(), doc.getSourcePath(), doc.getChunkIndex(), excerpt));
            }
        }
        final List<Citation> citations = builtCitations;

        // 5. 构建完整 prompt（根据 RAG 可用性选择不同 system prompt）
        List<Map<String, String>> promptMessages;
        if (ragAvailable) {
            String contextText = formatContext(relevantDocs);
            promptMessages = buildRagPromptMessages(sid, contextText);
        } else {
            promptMessages = buildPureChatPromptMessages(sid);
        }

        // 6. 压缩上下文（如果需要）
        memoryService.compressIfNeeded(sid);

        // 7. 流式调用 LLM
        StringBuilder fullAnswer = new StringBuilder();

        return llmService.streamChat(promptMessages)
                .map(token -> {
                    fullAnswer.append(token);
                    return ChatEvent.delta(token);
                })
                .concatWith(Flux.defer(() -> {
                    // 流结束后：保存助手回复 + 发送 citation 事件
                    String citationsJson = ragAvailable ? serializeCitations(citations) : null;
                    memoryService.addMessage(sid, "assistant", fullAnswer.toString(), citationsJson);

                    // 更新会话标题（如果是第一条消息）
                    updateSessionTitle(sid, message);

                    return Flux.just(ChatEvent.done(citations, sid, ragAvailable));
                }))
                .onErrorResume(e -> {
                    log.error("对话流式输出异常: sessionId={}, error={}", sid, e.getMessage(), e);
                    return Flux.just(
                            ChatEvent.delta("抱歉，处理您的问题时出现错误，请稍后重试。"),
                            ChatEvent.done(Collections.emptyList(), sid, false)
                    );
                });
    }

    @Override
    public String createSession(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(DEFAULT_SESSION_TITLE);
        chatSessionMapper.insert(session);
        log.info("创建新会话: sessionId={}, userId={}", sessionId, userId);
        return sessionId;
    }

    @Override
    public List<ChatMessageVO> getMessages(String sessionId, int page, int size) {
        IPage<ChatMessage> pageResult = chatMessageMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );
        return pageResult.getRecords().stream()
                .map(msg -> new ChatMessageVO(
                        msg.getRole(),
                        msg.getContent(),
                        msg.getCitationsJson(),
                        msg.getCreateTime() != null ? msg.getCreateTime().toString() : ""
                ))
                .collect(Collectors.toList());
    }

    /**
     * 格式化检索结果为带编号的上下文文本
     */
    private String formatContext(List<DocumentChunkDTO> docs) {
        if (docs.isEmpty()) {
            return "未找到相关参考资料。";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            DocumentChunkDTO doc = docs.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append(doc.getFileName() != null ? doc.getFileName() : "未知文档");
            sb.append(": ").append(doc.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 构建 RAG 模式的 prompt（system + 上下文 + 历史）
     */
    private List<Map<String, String>> buildRagPromptMessages(String sessionId, String contextText) {
        List<Map<String, String>> messages = new ArrayList<>();
        String fullSystemPrompt = ragSystemPrompt + "\n\n参考资料：\n" + contextText;
        messages.add(Map.of("role", "system", "content", fullSystemPrompt));
        messages.addAll(memoryService.getHistory(sessionId));
        return messages;
    }

    /**
     * 构建纯对话模式的 prompt（system + 历史，无上下文）
     */
    private List<Map<String, String>> buildPureChatPromptMessages(String sessionId) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", pureChatPrompt));
        messages.addAll(memoryService.getHistory(sessionId));
        return messages;
    }

    /**
     * 更新会话标题（用第一条用户消息的前20个字符）
     */
    private void updateSessionTitle(String sessionId, String firstMessage) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
        );
        if (session != null && DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
            String title = firstMessage.length() > 20 ? firstMessage.substring(0, 20) + "..." : firstMessage;
            session.setTitle(title);
            chatSessionMapper.updateById(session);
        }
    }

    /**
     * 序列化 citations 为 JSON
     */
    private String serializeCitations(List<Citation> citations) {
        if (citations.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            log.warn("序列化citations失败: {}", e.getMessage());
            return null;
        }
    }
}
