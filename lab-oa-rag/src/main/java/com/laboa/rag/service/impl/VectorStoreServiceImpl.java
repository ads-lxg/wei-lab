package com.laboa.rag.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.laboa.rag.dto.DocumentChunkDTO;
import com.laboa.rag.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量存储服务实现 - 基于 ES doc_chunks 索引
 * mapping 含 dense_vector 字段 (dims=2048, similarity=cosine)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final ElasticsearchClient elasticsearchClient;

    private static final String INDEX_NAME = "doc_chunks";
    private static final int EMBEDDING_DIMS = 2048;

    @Override
    public void createIndexIfNotExists() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                    .value();
            if (exists) {
                log.info("ES索引 '{}' 已存在，跳过创建", INDEX_NAME);
                return;
            }

            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("content", p -> p.text(t -> t))
                            .properties("embedding", p -> p
                                    .denseVector(dv -> dv
                                            .dims(EMBEDDING_DIMS)
                                            .similarity("cosine")
                                    )
                            )
                            .properties("fileName", p -> p.keyword(k -> k))
                            .properties("sourcePath", p -> p.keyword(k -> k))
                            .properties("chunkIndex", p -> p.integer(i -> i))
                            .properties("docType", p -> p.keyword(k -> k))
                            .properties("docId", p -> p.long_(l -> l))
                    )
            ));
            log.info("ES索引 '{}' 创建成功 (dims={}, similarity=cosine)", INDEX_NAME, EMBEDDING_DIMS);
        } catch (ElasticsearchException | IOException e) {
            log.error("创建ES索引失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void storeChunk(DocumentChunkDTO chunk) {
        try {
            Map<String, Object> doc = chunkToMap(chunk);
            elasticsearchClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(chunk.getId())
                    .document(doc)
            );
            log.debug("存储chunk成功: id={}, fileName={}, chunkIndex={}",
                    chunk.getId(), chunk.getFileName(), chunk.getChunkIndex());
        } catch (ElasticsearchException | IOException e) {
            log.error("存储chunk失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void storeChunks(List<DocumentChunkDTO> chunks) {
        if (chunks.isEmpty()) return;

        try {
            List<BulkOperation> operations = new ArrayList<>();
            for (DocumentChunkDTO chunk : chunks) {
                Map<String, Object> doc = chunkToMap(chunk);
                operations.add(BulkOperation.of(b -> b
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(chunk.getId())
                                .document(doc)
                        )
                ));
            }

            BulkResponse response = elasticsearchClient.bulk(BulkRequest.of(b -> b
                    .operations(operations)
                    .refresh(co.elastic.clients.elasticsearch._types.Refresh.True)
            ));

            // 检查 bulk 响应中的错误
            if (response.errors()) {
                int errorCount = 0;
                StringBuilder errorDetails = new StringBuilder();
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        errorCount++;
                        if (errorCount <= 5) { // 只记录前 5 个错误详情
                            errorDetails.append(String.format("\n  [%s] id=%s, reason=%s",
                                    item.id(), item.id(), item.error().reason()));
                        }
                    }
                }
                log.error("BULK 写入有 {} 个失败项（共 {} 项）, 前5个详情:{}",
                        errorCount, chunks.size(), errorDetails.toString());
            } else {
                log.info("批量存储 {} 个chunk到ES成功", chunks.size());
            }
        } catch (ElasticsearchException | IOException e) {
            log.error("批量存储chunk失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteByDocId(String docType, Long docId) {
        try {
            var response = elasticsearchClient.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m.term(t -> t.field("docType").value(docType)))
                                    .must(m -> m.term(t -> t.field("docId").value(docId)))
                            )
                    )
                    .refresh(true)
            );
            log.info("删除 docType={}, docId={} 的所有chunk，删除数量={}", docType, docId,
                    response.deleted() != null ? response.deleted() : "unknown");
        } catch (ElasticsearchException | IOException e) {
            log.error("删除文档chunk失败: docType={}, docId={}, error={}", docType, docId, e.getMessage(), e);
        }
    }

    @Override
    public List<Long> findAllDocIds(String docType) {
        List<Long> ids = new ArrayList<>();
        try {
            Long lastDocId = null;
            while (true) {
                final Long searchAfter = lastDocId;
                var response = elasticsearchClient.search(s -> {
                    var builder = s.index(INDEX_NAME)
                            .size(1000)
                            .query(q -> q.term(t -> t.field("docType").value(docType)))
                            .source(src -> src.filter(f -> f.includes("docId")))
                            .sort(so -> so.field(f -> f.field("docId").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
                    if (searchAfter != null) {
                        builder.searchAfter(String.valueOf(searchAfter));
                    }
                    return builder;
                }, Map.class);

                var hits = response.hits().hits();
                if (hits.isEmpty()) break;

                for (var hit : hits) {
                    Map<String, Object> source = hit.source();
                    if (source != null && source.get("docId") != null) {
                        Long did = ((Number) source.get("docId")).longValue();
                        ids.add(did);
                        lastDocId = did;
                    }
                }
            }
        } catch (Exception e) {
            log.error("扫描ES doc_chunks索引失败: docType={}, error={}", docType, e.getMessage(), e);
        }
        return ids;
    }

    /**
     * 将 DTO 转为 ES 文档 Map
     */
    private Map<String, Object> chunkToMap(DocumentChunkDTO chunk) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("content", chunk.getContent());
        doc.put("embedding", chunk.getEmbedding());
        doc.put("fileName", chunk.getFileName());
        doc.put("sourcePath", chunk.getSourcePath());
        doc.put("chunkIndex", chunk.getChunkIndex());
        doc.put("docType", chunk.getDocType());
        doc.put("docId", chunk.getDocId());
        return doc;
    }
}
