package com.laboa.rag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.Result;
import com.laboa.doc.entity.MdDocument;
import com.laboa.doc.service.MdDocumentService;
import com.laboa.file.service.FileService;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.service.LiteratureService;
import com.laboa.rag.dto.ChatRequest;
import com.laboa.rag.dto.DocumentChunkDTO;
import com.laboa.rag.service.ChatService;
import com.laboa.rag.service.ChatService.ChatEvent;
import com.laboa.rag.service.ChatService.ChatMessageVO;
import com.laboa.rag.service.ChatService.SessionVO;
import com.laboa.rag.service.RetrievalService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * RAG 聊天控制器
 * - POST /api/chat/stream: 流式对话（SSE）
 * - POST /api/session: 创建新会话
 * - GET /api/session/{sessionId}/messages: 获取历史消息
 */
@Slf4j
@RestController
public class RagController {

    private final ChatService chatService;
    private final ElasticsearchClient elasticsearchClient;
    private final RetrievalService retrievalService;
    private final FileService fileService;
    private final LiteratureService literatureService;
    private final MdDocumentService mdDocumentService;

    /** SSE 专用线程池 */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-emitter");
        t.setDaemon(true);
        return t;
    });

    public RagController(ChatService chatService, ElasticsearchClient elasticsearchClient,
                          RetrievalService retrievalService, FileService fileService,
                          LiteratureService literatureService, MdDocumentService mdDocumentService) {
        this.chatService = chatService;
        this.elasticsearchClient = elasticsearchClient;
        this.retrievalService = retrievalService;
        this.fileService = fileService;
        this.literatureService = literatureService;
        this.mdDocumentService = mdDocumentService;
    }

    /**
     * 流式 RAG 对话
     * 使用 SseEmitter（Servlet 原生 SSE），确保在 Tomcat 下真正流式输出
     */
    @SaCheckPermission("rag:stream")
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request,
                                  HttpServletResponse response) {
        // 确保 SSE 不被任何代理/容器缓冲
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        Long userId = StpUtil.getLoginIdAsLong();
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        // 超时 3 分钟
        SseEmitter emitter = new SseEmitter(180_000L);

        if (message == null || message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"消息不能为空\"}"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 先发送 info 事件
        try {
            emitter.send(SseEmitter.event().name("info").data("{\"status\":\"thinking\",\"message\":\"模型思考中...\"}"));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // 在独立线程中订阅 Flux 并转发到 SseEmitter
        sseExecutor.execute(() -> {
            try {
                chatService.streamChat(sessionId, userId, message)
                        .subscribe(
                                event -> {
                                    try {
                                        if ("delta".equals(event.type())) {
                                            emitter.send(SseEmitter.event().name("delta").data(event.content()));
                                        } else {
                                            emitter.send(SseEmitter.event().name("done").data(buildDonePayload(event)));
                                        }
                                    } catch (IOException e) {
                                        emitter.completeWithError(e);
                                    }
                                },
                                emitter::completeWithError,
                                emitter::complete
                        );
            } catch (Exception e) {
                log.error("SSE流式对话异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 创建新会话
     */
    @SaCheckPermission("rag:session")
    @PostMapping("/api/session")
    public Result<Map<String, String>> createSession() {
        Long userId = StpUtil.getLoginIdAsLong();
        String sessionId = chatService.createSession(userId);
        return Result.success(Map.of("sessionId", sessionId));
    }

    /**
     * 获取会话历史消息
     */
    @SaCheckPermission("rag:session")
    @GetMapping("/api/session/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getMessages(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<ChatMessageVO> messages = chatService.getMessages(sessionId, userId, page, size);
        return Result.success(messages);
    }

    /**
     * 构建 done 事件的 JSON payload
     */
    private String buildDonePayload(ChatEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"sessionId\":\"").append(event.sessionId() != null ? event.sessionId() : "").append("\"");
        sb.append(",\"ragAvailable\":").append(event.ragAvailable());
        if (event.citations() != null && !event.citations().isEmpty()) {
            sb.append(",\"citations\":[");
            for (int i = 0; i < event.citations().size(); i++) {
                var c = event.citations().get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"referenceNumber\":").append(c.getReferenceNumber())
                  .append(",\"fileName\":\"").append(escapeJson(c.getFileName())).append("\"")
                  .append(",\"sourcePath\":\"").append(escapeJson(c.getSourcePath())).append("\"")
                  .append(",\"chunkIndex\":").append(c.getChunkIndex())
                  .append(",\"excerpt\":\"").append(escapeJson(c.getExcerpt())).append("\"")
                  .append(",\"docType\":\"").append(escapeJson(c.getDocType())).append("\"")
                  .append(",\"docId\":\"").append(escapeJson(c.getDocId())).append("\"")
                  .append("}");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    /**
     * 查询 doc_chunks 的分块列表（按 docType + docId）
     */
    @GetMapping("/api/search/chunks")
    public Result<List<Map<String, Object>>> queryChunks(
            @RequestParam("docType") String docType,
            @RequestParam("docId") Long docId) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index("doc_chunks")
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m.term(t -> t.field("docType").value(docType)))
                                    .must(m -> m.term(t -> t.field("docId").value(docId)))
                            )
                    )
                    .sort(so -> so.field(f -> f.field("chunkIndex").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)))
                    .size(200)
            , Map.class);

            List<Map<String, Object>> result = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                Map<String, Object> src = hit.source();
                if (src == null) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", hit.id());
                item.put("chunkIndex", src.get("chunkIndex"));
                item.put("content", src.get("content"));
                item.put("fileName", src.get("fileName"));
                item.put("sourcePath", src.get("sourcePath"));
                result.add(item);
            }
            return Result.success(result);
        } catch (IOException e) {
            log.error("查询doc_chunks失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 向量检索测试
     */
    @GetMapping("/api/search/vector-search")
    public Result<List<Map<String, Object>>> vectorSearch(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        try {
            List<DocumentChunkDTO> chunks = retrievalService.retrieve(query, topK);
            List<Map<String, Object>> result = new ArrayList<>();
            for (DocumentChunkDTO c : chunks) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", c.getContent());
                item.put("fileName", c.getFileName());
                item.put("chunkIndex", c.getChunkIndex());
                item.put("docType", c.getDocType());
                item.put("docId", c.getDocId());
                result.add(item);
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("向量检索失败: {}", e.getMessage(), e);
            return Result.error("向量检索失败: " + e.getMessage());
        }
    }

    /**
     * 删除会话
     */
    @SaCheckPermission("rag:session")
    @PostMapping("/api/session/{sessionId}/delete")
    public Result<Void> deleteSession(@PathVariable("sessionId") String sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        chatService.deleteSession(sessionId, userId);
        return Result.success();
    }

    /**
     * 批量删除会话
     */
    @SaCheckPermission("rag:session")
    @PostMapping("/api/session/batch-delete")
    public Result<Void> batchDeleteSessions(@RequestBody List<String> sessionIds) {
        Long userId = StpUtil.getLoginIdAsLong();
        chatService.batchDeleteSessions(sessionIds, userId);
        return Result.success();
    }

    /**
     * 获取用户所有会话列表
     */
    @SaCheckPermission("rag:session")
    @GetMapping("/api/session/list")
    public Result<List<SessionVO>> listSessions() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<SessionVO> sessions = chatService.listSessions(userId);
        return Result.success(sessions);
    }

    /**
     * 搜索会话（按标题或消息内容模糊匹配）
     */
    @SaCheckPermission("rag:session")
    @GetMapping("/api/session/search")
    public Result<List<SessionVO>> searchSessions(
            @RequestParam("keyword") String keyword) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<SessionVO> sessions = chatService.searchSessions(userId, keyword);
        return Result.success(sessions);
    }

    /**
     * 导出会话
     */
    @SaCheckPermission("rag:session")
    @GetMapping("/api/session/{sessionId}/export")
    public Result<Map<String, String>> exportSession(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "format", defaultValue = "json") String format) {
        String content = chatService.exportSession(sessionId, format);
        return Result.success(Map.of("format", format, "content", content));
    }

    /**
     * 修改用户消息并重新生成助手回复（SSE流式）
     */
    @SaCheckPermission("rag:stream")
    @PostMapping(value = "/api/chat/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateFromMessage(@RequestBody RegenerateRequest request,
                                              HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        SseEmitter emitter = new SseEmitter(180_000L);
        Long userId = StpUtil.getLoginIdAsLong();

        if (request.getMessageId() == null || request.getMessageId().isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"messageId不能为空\"}"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }
        if (request.getNewContent() == null || request.getNewContent().isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"新消息内容不能为空\"}"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        Long msgId;
        try {
            msgId = Long.parseLong(request.getMessageId());
        } catch (NumberFormatException e) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"messageId格式错误\"}"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 先发送 info 事件
        try {
            emitter.send(SseEmitter.event().name("info").data("{\"status\":\"thinking\",\"message\":\"重新生成中...\"}"));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        final Long finalMsgId = msgId;
        sseExecutor.execute(() -> {
            try {
                chatService.regenerateFromMessage(request.getSessionId(), finalMsgId, request.getNewContent(), userId)
                        .subscribe(
                                event -> {
                                    try {
                                        if ("delta".equals(event.type())) {
                                            emitter.send(SseEmitter.event().name("delta").data(event.content()));
                                        } else {
                                            emitter.send(SseEmitter.event().name("done").data(buildDonePayload(event)));
                                        }
                                    } catch (IOException e) {
                                        emitter.completeWithError(e);
                                    }
                                },
                                emitter::completeWithError,
                                emitter::complete
                        );
            } catch (Exception e) {
                log.error("SSE重新生成异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 重新生成请求DTO
     */
    @lombok.Data
    public static class RegenerateRequest {
        private String sessionId;
        private String messageId;
        private String newContent;
    }

    /**
     * 统一批量下载引用来源文件（文献 + 内部文档），打包为 ZIP
     */
    @SaCheckPermission("rag:citation")
    @PostMapping("/api/citation/batch-download")
    public void batchDownloadCitations(@RequestBody CitationBatchDownloadRequest request,
                                       HttpServletResponse response) {
        Long userId = StpUtil.getLoginIdAsLong();

        Map<Long, String> fileMap = new LinkedHashMap<>();

        for (CitationBatchDownloadRequest.CitationItem item : request.getItems()) {
            try {
                Long fileId = null;
                String fileName = null;
                if ("literature".equals(item.getDocType()) && item.getDocId() != null) {
                    Literature lit = literatureService.getById(Long.valueOf(item.getDocId()));
                    if (lit != null) {
                        fileId = lit.getFileId();
                        fileName = lit.getFileName() != null ? lit.getFileName() : (lit.getTitle() + ".pdf");
                        literatureService.recordDownload(lit.getId(), userId);
                    }
                } else if ("doc".equals(item.getDocType()) && item.getDocId() != null) {
                    MdDocument doc = mdDocumentService.getById(Long.valueOf(item.getDocId()));
                    if (doc != null) {
                        fileId = doc.getFileId();
                        fileName = doc.getTitle() + "." + (doc.getFileType() != null ? doc.getFileType() : "md");
                    }
                }
                if (fileId != null && !fileMap.containsKey(fileId)) {
                    fileMap.put(fileId, fileName);
                }
            } catch (Exception e) {
                log.warn("获取引用文件信息失败: docType={}, docId={}", item.getDocType(), item.getDocId(), e);
            }
        }

        if (fileMap.isEmpty()) {
            throw new BusinessException("没有可下载的文件");
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=citations.zip");

        try (OutputStream os = response.getOutputStream();
             ZipOutputStream zos = new ZipOutputStream(os)) {

            for (Map.Entry<Long, String> entry : fileMap.entrySet()) {
                long fileId = entry.getKey();
                String fileName = entry.getValue();
                try (InputStream is = fileService.getFileStream(fileId)) {
                    ZipEntry zipEntry = new ZipEntry(fileName);
                    zos.putNextEntry(zipEntry);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                } catch (Exception e) {
                    log.error("ZIP打包-文件下载失败: fileId={}, name={}", fileId, fileName, e);
                }
            }
            zos.finish();
        } catch (Exception e) {
            log.error("批量下载ZIP失败", e);
            throw new BusinessException("批量下载失败");
        }
    }

    /**
     * 批量下载请求DTO
     */
    @lombok.Data
    public static class CitationBatchDownloadRequest {
        private List<CitationItem> items;

        @lombok.Data
        public static class CitationItem {
            private String docType;  // literature / doc
            private String docId;
        }
    }

}
