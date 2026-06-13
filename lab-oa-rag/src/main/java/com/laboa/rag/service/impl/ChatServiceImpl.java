package com.laboa.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboa.common.exception.BusinessException;
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

import java.time.LocalDateTime;
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

    @Value("${rag.system-prompt:你是一个智能助手，兼具专业知识与灵活应变能力。\n\n## 回答规则\n1. 当参考资料中包含与问题相关的内容时，优先基于参考资料回答，并用[编号]标注出处。在引用基础上可适当补充专业背景和延伸解释，使回答更完整。\n2. 当参考资料中没有直接相关内容，但你自身知识可以回答时，直接给出专业、详细的回答。\n3. 当问题涉及操作指引、流程说明等场景时，给出清晰的步骤式回答。\n4. 保持回答结构清晰，适当使用标题、列表、加粗等格式提升可读性。\n5. 如果对问题不确定，坦诚说明，不要编造信息。}")
    private String ragSystemPrompt;

    @Value("${rag.pure-chat-prompt:你是一个智能助手，擅长用专业、清晰的方式回答各类问题。回答时注意结构化表达，必要时使用列表和分步说明。如果不确定，请坦诚说明。}")
    private String pureChatPrompt;

    @Value("${rag.top-k:5}")
    private int topK;

    /** 检索分数阈值：低于最高分 * 此比例的结果将被过滤 */
    @Value("${rag.score-ratio-threshold:0.5}")
    private double scoreRatioThreshold;

    private static final String DEFAULT_SESSION_TITLE = "新对话";

    @Override
    public Flux<ChatEvent> streamChat(String sessionId, Long userId, String message) {
        // 1. 确保会话存在
        final String sid = (sessionId == null || sessionId.isBlank()) ? createSession(userId) : sessionId;

        // 2. 保存用户消息
        memoryService.addMessage(sid, "user", message, null);

        // 3. 执行RAG管道（不保存用户消息）
        return doRagPipeline(sid, message, true);
    }

    /**
     * 执行RAG管道核心逻辑（检索→增强→LLM流式→保存助手回复）
     * @param sid 会话ID
     * @param message 用户消息
     * @param updateTitle 是否更新会话标题
     */
    private Flux<ChatEvent> doRagPipeline(String sid, String message, boolean updateTitle) {
        // 1. 尝试检索相关文档（失败则降级为纯对话）
        // 初始检索 2*topK 条，再按分数阈值过滤
        List<DocumentChunkDTO> relevantDocs = Collections.emptyList();
        boolean isRagAvailable = false;
        try {
            List<DocumentChunkDTO> rawDocs = retrievalService.hybridRetrieve(message, topK * 2);
            relevantDocs = filterByScore(rawDocs);
            isRagAvailable = !relevantDocs.isEmpty();
        } catch (Exception e) {
            log.warn("RAG检索失败，降级为纯LLM对话: {}", e.getMessage());
        }
        final boolean ragAvailable = isRagAvailable;

        // 2. 构建 citations（仅 RAG 模式）
        List<Citation> builtCitations = new ArrayList<>();
        if (ragAvailable) {
            for (int i = 0; i < relevantDocs.size(); i++) {
                DocumentChunkDTO doc = relevantDocs.get(i);
                String excerpt = doc.getContent();
                if (excerpt != null && excerpt.length() > 200) {
                    excerpt = excerpt.substring(0, 200) + "...";
                }
                builtCitations.add(new Citation(i + 1, doc.getFileName(), doc.getSourcePath(), doc.getChunkIndex(), excerpt, doc.getDocType(), doc.getDocId()));
            }
        }
        final List<Citation> citations = builtCitations;

        // 3. 构建完整 prompt
        List<Map<String, String>> promptMessages;
        if (ragAvailable) {
            String contextText = formatContext(relevantDocs);
            promptMessages = buildRagPromptMessages(sid, contextText);
        } else {
            promptMessages = buildPureChatPromptMessages(sid);
        }

        // 4. 压缩上下文
        memoryService.compressIfNeeded(sid);

        // 5. 流式调用 LLM
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

                    if (updateTitle) {
                        updateSessionTitle(sid, message);
                    }

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
                        msg.getId(),
                        msg.getRole(),
                        msg.getContent(),
                        msg.getCitationsJson(),
                        msg.getCreateTime() != null ? msg.getCreateTime().toString() : ""
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<SessionVO> listSessions(Long userId) {
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdateTime)
        );
        List<SessionVO> result = new ArrayList<>();
        for (ChatSession s : sessions) {
            Long msgCount = chatMessageMapper.selectCount(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, s.getSessionId())
            );
            result.add(new SessionVO(
                    s.getSessionId(),
                    s.getTitle(),
                    s.getCreateTime() != null ? s.getCreateTime().toString() : "",
                    msgCount
            ));
        }
        return result;
    }

    @Override
    public List<SessionVO> searchSessions(Long userId, String keyword) {
        // 1. 按标题模糊匹配
        List<ChatSession> titleMatches = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .like(ChatSession::getTitle, keyword)
                        .orderByDesc(ChatSession::getUpdateTime)
        );

        // 2. 按消息内容模糊匹配（找出包含关键词的消息所属的sessionId）
        List<ChatMessage> contentMatches = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .like(ChatMessage::getContent, keyword)
        );
        Set<String> contentSessionIds = contentMatches.stream()
                .map(ChatMessage::getSessionId)
                .collect(Collectors.toSet());

        // 3. 合并去重
        Set<String> seenIds = new HashSet<>();
        List<SessionVO> result = new ArrayList<>();

        for (ChatSession s : titleMatches) {
            if (seenIds.add(s.getSessionId())) {
                Long msgCount = chatMessageMapper.selectCount(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, s.getSessionId())
                );
                result.add(new SessionVO(
                        s.getSessionId(),
                        s.getTitle(),
                        s.getCreateTime() != null ? s.getCreateTime().toString() : "",
                        msgCount
                ));
            }
        }

        // 内容匹配但标题不匹配的会话
        for (String sid : contentSessionIds) {
            if (seenIds.add(sid)) {
                ChatSession s = chatSessionMapper.selectOne(
                        new LambdaQueryWrapper<ChatSession>()
                                .eq(ChatSession::getSessionId, sid)
                                .eq(ChatSession::getUserId, userId)
                                .eq(ChatSession::getDeleted, 0)
                );
                if (s != null) {
                    Long msgCount = chatMessageMapper.selectCount(
                            new LambdaQueryWrapper<ChatMessage>()
                                    .eq(ChatMessage::getSessionId, sid)
                    );
                    result.add(new SessionVO(
                            s.getSessionId(),
                            s.getTitle(),
                            s.getCreateTime() != null ? s.getCreateTime().toString() : "",
                            msgCount
                    ));
                }
            }
        }

        return result;
    }

    @Override
    public void deleteSession(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        // 使用 deleteById 触发 @TableLogic 逻辑删除
        chatSessionMapper.deleteById(session.getId());
    }

    @Override
    public String exportSession(String sessionId, String format) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
        );
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );

        if ("markdown".equalsIgnoreCase(format)) {
            return exportAsMarkdown(session, messages);
        }
        return exportAsJson(session, messages);
    }

    @Override
    public Flux<ChatEvent> regenerateFromMessage(String sessionId, Long messageId, String newContent, Long userId) {
        // 1. 查找目标消息
        ChatMessage targetMsg = chatMessageMapper.selectById(messageId);
        if (targetMsg == null) {
            return Flux.just(
                    ChatEvent.delta("消息不存在，无法重新生成。"),
                    ChatEvent.done(Collections.emptyList(), sessionId, false)
            );
        }
        if (!"user".equals(targetMsg.getRole())) {
            return Flux.just(
                    ChatEvent.delta("只能修改用户消息并重新生成。"),
                    ChatEvent.done(Collections.emptyList(), sessionId, false)
            );
        }

        // 2. 覆盖式更新：修改原消息内容 + 设置编辑时间
        targetMsg.setContent(newContent);
        targetMsg.setEditedAt(LocalDateTime.now());
        chatMessageMapper.updateById(targetMsg);
        log.info("消息已编辑: messageId={}, sessionId={}", messageId, sessionId);

        // 3. 删除该消息之后的所有消息（id > messageId，雪花ID严格递增）
        int deleted = chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .gt(ChatMessage::getId, messageId)
        );
        log.info("删除后续消息: sessionId={}, messageId={}, 删除{}条", sessionId, messageId, deleted);

        // 4. 更新会话的更新时间
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
        );
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }

        // 5. 以修改后的消息重新触发RAG管道（不保存用户消息，因为已经更新了）
        return doRagPipeline(sessionId, newContent, false);
    }

    private String exportAsJson(ChatSession session, List<ChatMessage> messages) {
        try {
            Map<String, Object> export = new LinkedHashMap<>();
            export.put("sessionId", session != null ? session.getSessionId() : "");
            export.put("title", session != null ? session.getTitle() : "");
            export.put("createTime", session != null && session.getCreateTime() != null ? session.getCreateTime().toString() : "");
            if (session != null && session.getSummary() != null) {
                export.put("summary", session.getSummary());
            }

            List<Map<String, Object>> msgList = new ArrayList<>();
            for (ChatMessage msg : messages) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
                m.put("createTime", msg.getCreateTime() != null ? msg.getCreateTime().toString() : "");
                if (msg.getCitationsJson() != null) {
                    m.put("citations", msg.getCitationsJson());
                }
                msgList.add(m);
            }
            export.put("messages", msgList);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (JsonProcessingException e) {
            log.error("导出JSON失败: {}", e.getMessage());
            return "{\"error\":\"导出失败\"}";
        }
    }

    private String exportAsMarkdown(ChatSession session, List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(session != null && session.getTitle() != null ? session.getTitle() : "对话记录").append("\n\n");
        if (session != null) {
            sb.append("**会话ID**: ").append(session.getSessionId()).append("\n\n");
            sb.append("**创建时间**: ").append(session.getCreateTime() != null ? session.getCreateTime() : "").append("\n\n");
            if (session.getSummary() != null && !session.getSummary().isBlank()) {
                sb.append("**对话摘要**: ").append(session.getSummary()).append("\n\n");
            }
        }
        sb.append("---\n\n");

        for (ChatMessage msg : messages) {
            String roleLabel = "user".equals(msg.getRole()) ? "👤 用户" :
                              "assistant".equals(msg.getRole()) ? "🤖 助手" : "⚙️ 系统";
            sb.append("### ").append(roleLabel);
            if (msg.getCreateTime() != null) {
                sb.append(" <small>").append(msg.getCreateTime()).append("</small>");
            }
            sb.append("\n\n");
            sb.append(msg.getContent()).append("\n\n");
            if (msg.getCitationsJson() != null) {
                sb.append("> 📎 引用: ").append(msg.getCitationsJson()).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 根据分数阈值过滤检索结果：保留分数 >= 最高分 * scoreRatioThreshold 的结果
     * 这样高分查询可以返回更多相关结果，低相关性的查询自动减少返回数量
     */
    private List<DocumentChunkDTO> filterByScore(List<DocumentChunkDTO> docs) {
        if (docs.isEmpty()) return docs;
        double maxScore = docs.stream().mapToDouble(DocumentChunkDTO::getScore).max().orElse(0);
        if (maxScore <= 0) return docs; // 无分数信息时不过滤
        double threshold = maxScore * scoreRatioThreshold;
        List<DocumentChunkDTO> filtered = docs.stream()
                .filter(d -> d.getScore() >= threshold)
                .toList();
        log.info("分数过滤: 总数={}, 最高分={}, 阈值={}, 保留={}",
                docs.size(), String.format("%.4f", maxScore), String.format("%.4f", threshold), filtered.size());
        return filtered;
    }

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
