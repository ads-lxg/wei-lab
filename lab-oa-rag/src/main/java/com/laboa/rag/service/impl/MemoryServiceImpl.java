package com.laboa.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.rag.entity.ChatMessage;
import com.laboa.rag.entity.ChatSession;
import com.laboa.rag.mapper.ChatMessageMapper;
import com.laboa.rag.mapper.ChatSessionMapper;
import com.laboa.rag.service.LLMService;
import com.laboa.rag.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话记忆服务实现
 * - 短期记忆：最近10轮对话
 * - 上下文压缩：token数超过阈值时生成摘要
 * - 历史持久化：MySQL chat_message 表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final LLMService llmService;

    /** 保留最近10轮对话 */
    private static final int MAX_ROUNDS = 10;
    /** DeepSeek 128K 上下文的75% ≈ 96000 token */
    private static final int TOKEN_LIMIT = 96000;

    @Override
    public List<Map<String, String>> getHistory(String sessionId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );

        // 如果有压缩摘要，放在最前面
        List<Map<String, String>> result = new ArrayList<>();
        ChatSession session = getSession(sessionId);
        if (session != null && session.getSummary() != null && !session.getSummary().isBlank()) {
            result.add(Map.of("role", "system", "content", "以下是之前对话的摘要：\n" + session.getSummary()));
        }

        for (ChatMessage msg : messages) {
            result.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
        return result;
    }

    @Override
    public void addMessage(String sessionId, String role, String content, String citationsJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCitationsJson(citationsJson);
        chatMessageMapper.insert(message);
    }

    @Override
    public int estimateTokens(String sessionId, String contextText, String systemPrompt) {
        // 粗略估算: 字符数 / 4
        int totalChars = 0;
        if (systemPrompt != null) totalChars += systemPrompt.length();
        if (contextText != null) totalChars += contextText.length();

        List<Map<String, String>> history = getHistory(sessionId);
        for (Map<String, String> msg : history) {
            totalChars += msg.get("content").length();
        }
        return totalChars / 4;
    }

    @Override
    public void compressIfNeeded(String sessionId) {
        int estimatedTokens = estimateTokens(sessionId, null, null);
        if (estimatedTokens < TOKEN_LIMIT) {
            return;
        }

        log.info("会话 {} token数({})超过阈值({})，开始压缩", sessionId, estimatedTokens, TOKEN_LIMIT);

        // 获取最早的5轮对话（10条消息）用于压缩
        List<ChatMessage> allMessages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );

        if (allMessages.size() <= 10) {
            return; // 消息太少，不压缩
        }

        // 取前10条（5轮）生成摘要
        List<ChatMessage> toCompress = allMessages.subList(0, 10);
        StringBuilder compressText = new StringBuilder("请将以下对话内容压缩为简洁的摘要，保留关键信息：\n\n");
        for (ChatMessage msg : toCompress) {
            compressText.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n\n");
        }

        List<Map<String, String>> summaryMessages = List.of(
                Map.of("role", "user", "content", compressText.toString())
        );

        String summary = llmService.chat(summaryMessages);
        if (summary == null || summary.isBlank()) {
            log.warn("摘要生成失败，跳过压缩");
            return;
        }

        // 更新会话摘要
        ChatSession session = getSession(sessionId);
        if (session != null) {
            String existingSummary = session.getSummary() != null ? session.getSummary() : "";
            session.setSummary(existingSummary + "\n" + summary);
            chatSessionMapper.updateById(session);
        }

        // 删除已压缩的消息
        for (ChatMessage msg : toCompress) {
            chatMessageMapper.deleteById(msg.getId());
        }

        log.info("会话 {} 压缩完成，删除了 {} 条消息", sessionId, toCompress.size());
    }

    private ChatSession getSession(String sessionId) {
        return chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
        );
    }
}
