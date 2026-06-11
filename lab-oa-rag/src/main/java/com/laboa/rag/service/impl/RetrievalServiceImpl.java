package com.laboa.rag.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.laboa.rag.dto.DocumentChunkDTO;
import com.laboa.rag.service.EmbeddingService;
import com.laboa.rag.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 检索服务实现 - ES k-NN 向量检索 + BM25 混合检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingService embeddingService;

    private static final String INDEX_NAME = "doc_chunks";

    @Override
    public List<DocumentChunkDTO> retrieve(String query, int topK) {
        if (!isIndexAvailable()) {
            return Collections.emptyList();
        }
        float[] queryVector = embeddingService.embed(query);
        if (queryVector.length == 0) {
            // Embedding 不可用 → 降级为 BM25 文本检索
            log.info("Embedding API 不可用，降级为 BM25 文本检索");
            return bm25Search(query, topK);
        }
        return knnSearch(queryVector, topK);
    }

    @Override
    public List<DocumentChunkDTO> hybridRetrieve(String query, int topK) {
        if (!isIndexAvailable()) {
            return Collections.emptyList();
        }

        float[] queryVector = embeddingService.embed(query);
        if (queryVector.length == 0) {
            // Embedding 不可用 → 降级为纯 BM25 文本检索
            log.info("Embedding API 不可用，降级为 BM25 文本检索 (doc_chunks)");
            return bm25Search(query, topK);
        }

        try {
            // 混合检索: kNN 向量检索 + BM25 关键词检索
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(INDEX_NAME)
                    .size(topK)
                    .query(q -> q
                            .bool(b -> b
                                    .should(knnQuery(queryVector))
                                    .should(bm25Query(query))
                                    .minimumShouldMatch("1")
                            )
                    ), Map.class);

            return parseHits(response);
        } catch (ElasticsearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.info("ES索引 '{}' 尚未创建，无RAG数据可检索", INDEX_NAME);
                return Collections.emptyList();
            }
            log.warn("混合检索失败，降级为纯BM25检索: {}", e.getMessage());
            return bm25Search(query, topK);
        } catch (IOException e) {
            log.warn("混合检索IO异常，降级为纯BM25检索: {}", e.getMessage());
            return bm25Search(query, topK);
        }
    }

    /**
     * 快速检查 ES 索引是否存在（有数据才走 RAG 检索）
     */
    private boolean isIndexAvailable() {
        try {
            return elasticsearchClient.indices().exists(e -> e.index(INDEX_NAME)).value();
        } catch (IOException e) {
            log.warn("检查ES索引是否存在时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 纯 k-NN 向量检索
     */
    private List<DocumentChunkDTO> knnSearch(float[] queryVector, int topK) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(INDEX_NAME)
                    .size(topK)
                    .query(knnQuery(queryVector)), Map.class);
            return parseHits(response);
        } catch (ElasticsearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.warn("ES索引 '{}' 尚未创建，检索返回空结果", INDEX_NAME);
                return Collections.emptyList();
            }
            log.error("kNN检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (IOException e) {
            log.error("kNN检索IO异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建 kNN 向量查询
     */
    private Query knnQuery(float[] queryVector) {
        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            vectorList.add(v);
        }
        return Query.of(q -> q
                .knn(k -> k
                        .field("embedding")
                        .queryVector(vectorList)
                        .numCandidates(50L)
                )
        );
    }

    /**
     * 构建 BM25 关键词查询
     */
    private Query bm25Query(String query) {
        return Query.of(q -> q
                .match(m -> m
                        .field("content")
                        .query(query)
                )
        );
    }

    /**
     * 纯 BM25 文本检索（Embedding API 不可用时的降级方案）
     * 在 doc_chunks 索引上执行关键词匹配
     */
    private List<DocumentChunkDTO> bm25Search(String query, int topK) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(INDEX_NAME)
                    .size(topK)
                    .query(bm25Query(query)), Map.class);
            return parseHits(response);
        } catch (ElasticsearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.warn("ES索引 '{}' 尚未创建，BM25检索返回空结果", INDEX_NAME);
                return Collections.emptyList();
            }
            log.error("BM25检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (IOException e) {
            log.error("BM25检索IO异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析 ES 搜索结果为 DTO 列表
     */
    @SuppressWarnings("unchecked")
    private List<DocumentChunkDTO> parseHits(SearchResponse<Map> response) {
        List<DocumentChunkDTO> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            if (source == null) continue;

            DocumentChunkDTO dto = new DocumentChunkDTO();
            dto.setId(hit.id());
            dto.setContent(String.valueOf(source.getOrDefault("content", "")));
            dto.setFileName(String.valueOf(source.getOrDefault("fileName", "")));
            dto.setSourcePath(String.valueOf(source.getOrDefault("sourcePath", "")));
            dto.setChunkIndex(source.get("chunkIndex") != null ? ((Number) source.get("chunkIndex")).intValue() : 0);
            dto.setDocType(String.valueOf(source.getOrDefault("docType", "")));
            dto.setDocId(source.get("docId") != null ? ((Number) source.get("docId")).longValue() : null);
            results.add(dto);
        }
        return results;
    }
}
