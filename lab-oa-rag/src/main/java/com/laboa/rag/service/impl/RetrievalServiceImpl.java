package com.laboa.rag.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.laboa.common.constant.MqConstants;
import com.laboa.doc.entity.MdDocument;
import com.laboa.doc.mapper.MdDocumentMapper;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.entity.RagFolder;
import com.laboa.literature.mapper.LiteratureMapper;
import com.laboa.literature.mapper.RagFolderMapper;
import com.laboa.rag.dto.DocumentChunkDTO;
import com.laboa.rag.service.EmbeddingService;
import com.laboa.rag.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检索服务实现 - ES k-NN 向量检索 + BM25 混合检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingService embeddingService;
    private final LiteratureMapper literatureMapper;
    private final MdDocumentMapper mdDocumentMapper;
    private final RagFolderMapper ragFolderMapper;

    private static final String INDEX_NAME = "doc_chunks";

    @Override
    public List<DocumentChunkDTO> retrieve(String query, int topK) {
        if (!isIndexAvailable()) {
            return Collections.emptyList();
        }
        float[] queryVector = embeddingService.embed(query);
        List<DocumentChunkDTO> candidates;
        if (queryVector.length == 0) {
            // Embedding 不可用 → 降级为 BM25 文本检索
            log.info("Embedding API 不可用，降级为 BM25 文本检索");
            candidates = bm25Search(query, topK * 3);
        } else {
            candidates = knnSearch(queryVector, topK * 3);
        }
        return filterValidChunks(candidates, topK);
    }

    @Override
    public List<DocumentChunkDTO> hybridRetrieve(String query, int topK) {
        if (!isIndexAvailable()) {
            return Collections.emptyList();
        }

        float[] queryVector = embeddingService.embed(query);
        List<DocumentChunkDTO> candidates;
        if (queryVector.length == 0) {
            log.info("Embedding API 不可用，降级为 BM25 文本检索 (doc_chunks)");
            candidates = bm25Search(query, topK * 3);
            return filterValidChunks(candidates, topK);
        }

        // 使用 RRF (Reciprocal Rank Fusion) 混合检索策略
        // 分别执行向量检索和BM25检索，再通过RRF融合排序
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(INDEX_NAME)
                    .size(topK * 3)
                    .query(q -> q
                            .bool(b -> b
                                    .should(knnQuery(queryVector))
                                    .should(bm25Query(query))
                                    .minimumShouldMatch("1")
                            )
                    )
                    // 使用 RRF 进行重排：将向量检索和BM25检索的排名融合
                    .rank(r -> r
                            .rrf(rrf -> rrf
                                    .windowSize(topK * 3L)
                                    .rankConstant(60L)
                            )
                    ), Map.class);

            candidates = parseHits(response);
        } catch (ElasticsearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.info("ES索引 '{}' 尚未创建，无RAG数据可检索", INDEX_NAME);
                return Collections.emptyList();
            }
            log.warn("混合检索失败，降级为纯BM25检索: {}", e.getMessage());
            candidates = bm25Search(query, topK * 3);
        } catch (IOException e) {
            log.warn("混合检索IO异常，降级为纯BM25检索: {}", e.getMessage());
            candidates = bm25Search(query, topK * 3);
        } catch (Exception e) {
            // RRF 可能不被当前 ES 版本支持，降级为手动 RRF
            log.warn("RRF检索异常，降级为手动RRF融合: {}", e.getMessage());
            candidates = manualRRF(query, queryVector, topK * 3);
        }
        return filterValidChunks(candidates, topK);
    }

    /**
     * 手动 RRF 融合：分别执行向量检索和BM25检索，通过RRF公式融合排序
     * RRF score = Σ 1/(k + rank_i)，k=60
     */
    private List<DocumentChunkDTO> manualRRF(String query, float[] queryVector, int topK) {
        final int RRF_K = 60;
        int candidateSize = Math.max(topK * 3, 20);

        List<DocumentChunkDTO> vectorResults = knnSearch(queryVector, candidateSize);
        List<DocumentChunkDTO> bm25Results = bm25Search(query, candidateSize);

        // 用 id 作为 key，计算 RRF 分数
        Map<String, Double> rrfScores = new java.util.HashMap<>();
        Map<String, DocumentChunkDTO> dtoMap = new java.util.HashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            DocumentChunkDTO dto = vectorResults.get(i);
            String id = dto.getId();
            rrfScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            dtoMap.putIfAbsent(id, dto);
        }
        for (int i = 0; i < bm25Results.size(); i++) {
            DocumentChunkDTO dto = bm25Results.get(i);
            String id = dto.getId();
            rrfScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            dtoMap.putIfAbsent(id, dto);
        }

        // 按 RRF 分数降序排列
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> dtoMap.get(e.getKey()))
                .collect(java.util.stream.Collectors.toList());
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
                        .numCandidates(100L)
                )
        );
    }

    /**
     * 构建 BM25 关键词查询 - 多字段匹配，提高召回率
     * minimumShouldMatch 设为 30%，跨语言场景下降低精确匹配门槛
     */
    private Query bm25Query(String query) {
        return Query.of(q -> q
                .multiMatch(m -> m
                        .fields("content", "fileName^2")
                        .query(query)
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                        .minimumShouldMatch("30%")
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
            Object docIdVal = source.get("docId");
            if (docIdVal instanceof Number) {
                dto.setDocId(((Number) docIdVal).longValue());
            } else if (docIdVal != null) {
                try {
                    dto.setDocId(Long.parseLong(String.valueOf(docIdVal)));
                } catch (NumberFormatException ignored) {
                }
            }
            dto.setScore(hit.score() != null ? hit.score() : 0.0);
            results.add(dto);
        }
        return results;
    }

    /**
     * 过滤掉已删除/回收站中的文档 chunk
     * 按 docType 回查 MySQL，只保留存在且未删除的文档
     * 对于文献，还需校验所属目录存在（排除目录已删除的孤儿文献）
     */
    private List<DocumentChunkDTO> filterValidChunks(List<DocumentChunkDTO> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 严格过滤：docType 和 docId 都不能为空，否则无法校验有效性，直接丢弃
        Map<String, Set<Long>> docIdGroups = candidates.stream()
                .filter(c -> c.getDocType() != null && !c.getDocType().isEmpty()
                        && c.getDocId() != null)
                .collect(Collectors.groupingBy(
                        DocumentChunkDTO::getDocType,
                        Collectors.mapping(DocumentChunkDTO::getDocId, Collectors.toSet())
                ));

        // 批量查询有效的 docId（未删除 + 文献需校验目录存在）
        Set<String> validKeys = new HashSet<>();
        for (Map.Entry<String, Set<Long>> entry : docIdGroups.entrySet()) {
            String docType = entry.getKey();
            Set<Long> docIds = entry.getValue();
            if (docIds.isEmpty()) continue;

            if (MqConstants.DOC_TYPE_LITERATURE.equals(docType)) {
                // 查询未删除的文献
                List<Literature> lits = literatureMapper.selectBatchIds(docIds);
                // 收集有效文献的 folderId，用于校验目录是否存在
                Set<Long> folderIds = lits.stream()
                        .map(Literature::getFolderId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Set<Long> validFolderIds = folderIds.isEmpty()
                        ? Collections.emptySet()
                        : new HashSet<>(ragFolderMapper.selectBatchIds(folderIds)
                                .stream()
                                .map(RagFolder::getId)
                                .collect(Collectors.toSet()));
                // 只保留：未删除 + folderId 不为空 + 目录存在且未删除
                lits.stream()
                        .filter(lit -> lit.getFolderId() != null && validFolderIds.contains(lit.getFolderId()))
                        .forEach(lit -> validKeys.add(docType + "_" + lit.getId()));
            } else if (MqConstants.DOC_TYPE_DOC.equals(docType)) {
                List<Long> validIds = mdDocumentMapper.selectBatchIds(docIds)
                        .stream()
                        .filter(doc -> doc.getDeleted() == null || doc.getDeleted() == 0)
                        .map(MdDocument::getId)
                        .collect(Collectors.toList());
                validIds.forEach(id -> validKeys.add(docType + "_" + id));
            }
        }

        // 保留有效文档的 chunk（严格校验：必须有 docType 和 docId 且通过有效性检查）
        return candidates.stream()
                .filter(c -> c.getDocType() != null && c.getDocId() != null
                        && validKeys.contains(c.getDocType() + "_" + c.getDocId()))
                .limit(topK)
                .collect(Collectors.toList());
    }
}
