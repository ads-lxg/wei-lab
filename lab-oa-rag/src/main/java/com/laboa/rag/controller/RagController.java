package com.laboa.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.laboa.common.result.Result;
import com.laboa.rag.dto.ChatRequest;
import com.laboa.rag.dto.DocumentChunkDTO;
import com.laboa.rag.service.ChatService;
import com.laboa.rag.service.ChatService.ChatEvent;
import com.laboa.rag.service.ChatService.ChatMessageVO;
import com.laboa.rag.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;

/**
 * RAG 聊天控制器
 * - POST /api/chat/stream: 流式对话（SSE）
 * - POST /api/session: 创建新会话
 * - GET /api/session/{sessionId}/messages: 获取历史消息
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RagController {

    private final ChatService chatService;
    private final ElasticsearchClient elasticsearchClient;
    private final RetrievalService retrievalService;

    /**
     * 流式 RAG 对话
     * 接收用户消息，通过 SSE 流式返回 LLM 回答
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        if (message == null || message.isBlank()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"error\":\"消息不能为空\"}")
                    .build());
        }

        return chatService.streamChat(sessionId, userId, message)
                .map(event -> {
                    if ("delta".equals(event.type())) {
                        return ServerSentEvent.<String>builder()
                                .event("delta")
                                .data(event.content())
                                .build();
                    } else {
                        // done 事件: 包含 citations、sessionId、ragAvailable
                        return ServerSentEvent.<String>builder()
                                .event("done")
                                .data(buildDonePayload(event))
                                .build();
                    }
                })
                .startWith(Flux.defer(() -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("info")
                                .data("{\"status\":\"thinking\",\"message\":\"模型思考中...\"}")
                                .build()
                )));
    }

    /**
     * 创建新会话
     */
    @PostMapping("/api/session")
    public Result<Map<String, String>> createSession() {
        Long userId = StpUtil.getLoginIdAsLong();
        String sessionId = chatService.createSession(userId);
        return Result.success(Map.of("sessionId", sessionId));
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/api/session/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getMessages(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        List<ChatMessageVO> messages = chatService.getMessages(sessionId, page, size);
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
     * 查看文档被 IK 分词器切成了多少个分块、每块的内容
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
     * 向量检索测试 — 输入自然语言查询，返回 doc_chunks 中最相似的 topK 个分块
     * 流程：query → Embedding → ES k-NN 搜索 → 返回 content
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
}
