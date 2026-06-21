package com.laboa.rag.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.laboa.rag.service.ResourceTextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES resource_text 索引服务实现
 * 存储 Tika 解析后的纯文本，用于全文检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceTextServiceImpl implements ResourceTextService {

    private final ElasticsearchClient esClient;

    private static final String INDEX_NAME = "resource_text";

    @Override
    public void createIndexIfNotExists() {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                    .value();
            if (exists) {
                log.info("ES索引 '{}' 已存在，跳过创建", INDEX_NAME);
                // 对已有索引，尝试补充 fileName 字段映射（幂等，已有则忽略）
                ensureFileNameMapping();
                return;
            }

            esClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("resourceId", p -> p.long_(l -> l))
                            .properties("title", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("fileName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("docType", p -> p.keyword(k -> k))
                            .properties("fileType", p -> p.keyword(k -> k))
                            .properties("text", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("createdAt", p -> p.date(d -> d))
                    )
            ));
            log.info("ES索引 '{}' 创建成功", INDEX_NAME);
        } catch (ElasticsearchException | IOException e) {
            log.error("创建ES索引失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 对已有索引补充 fileName 字段映射（兼容旧索引）
     * ES 允许向已有索引添加新字段映射
     */
    private void ensureFileNameMapping() {
        try {
            esClient.indices().putMapping(pm -> pm
                    .index(INDEX_NAME)
                    .properties("fileName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
            );
            log.info("ES索引 '{}' fileName 字段映射已确保存在", INDEX_NAME);
        } catch (Exception e) {
            // 字段已存在或IK分析器未安装，忽略
            log.debug("补充fileName映射跳过: {}", e.getMessage());
        }
    }

    @Override
    public void indexText(Long resourceId, String docType, String title,
                          String fileType, String text, String fileName) {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过ES索引: resourceId={}, docType={}", resourceId, docType);
            return;
        }

        try {
            // 截断过长文本（ES 单字段建议不超过 10MB）
            String truncated = text.length() > 10_000_000 ? text.substring(0, 10_000_000) : text;

            Map<String, Object> doc = new HashMap<>();
            doc.put("resourceId", resourceId);
            doc.put("title", title != null ? title : "");
            doc.put("fileName", fileName != null ? fileName : "");
            doc.put("docType", docType);
            doc.put("fileType", fileType);
            doc.put("text", truncated);
            doc.put("createdAt", LocalDateTime.now().toString());

            // 使用 resourceId + docType 拼接作为 ES 文档 _id（幂等覆盖）
            String esId = docType + "_" + resourceId;

            esClient.index(IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(esId)
                    .document(doc)
            ));

            log.info("ES文本索引成功: resourceId={}, docType={}, fileName={}, textLength={}",
                    resourceId, docType, fileName, truncated.length());
        } catch (ElasticsearchException | IOException e) {
            log.error("ES文本索引失败: resourceId={}, docType={}, error={}",
                    resourceId, docType, e.getMessage(), e);
        }
    }

    @Override
    public void deleteByResourceId(Long resourceId, String docType) {
        try {
            String esId = docType + "_" + resourceId;

            esClient.delete(DeleteRequest.of(d -> d
                    .index(INDEX_NAME)
                    .id(esId)
                    .refresh(co.elastic.clients.elasticsearch._types.Refresh.True)
            ));

            log.info("ES文本删除成功: resourceId={}, docType={}", resourceId, docType);
        } catch (ElasticsearchException e) {
            // 404 表示文档不存在，属正常情况
            if (e.getMessage() != null && e.getMessage().contains("not_found")) {
                log.info("ES文本已不存在（幂等）: resourceId={}, docType={}", resourceId, docType);
                return;
            }
            log.error("ES文本删除失败: resourceId={}, docType={}, error={}",
                    resourceId, docType, e.getMessage(), e);
        } catch (IOException e) {
            log.error("ES文本删除IO异常: resourceId={}, docType={}, error={}",
                    resourceId, docType, e.getMessage(), e);
        }
    }

    @Override
    public List<Long> findAllResourceIds(String docType) {
        List<Long> ids = new ArrayList<>();
        try {
            // 使用 search_after 分页遍历所有匹配文档
            Long lastResourceId = null;
            while (true) {
                final Long searchAfter = lastResourceId;
                var response = esClient.search(s -> {
                    var builder = s.index(INDEX_NAME)
                            .size(1000)
                            .query(q -> q.term(t -> t.field("docType").value(docType)))
                            .source(src -> src.filter(f -> f.includes("resourceId")))
                            .sort(so -> so.field(f -> f.field("resourceId").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
                    if (searchAfter != null) {
                        builder.searchAfter(String.valueOf(searchAfter));
                    }
                    return builder;
                }, Map.class);

                var hits = response.hits().hits();
                if (hits.isEmpty()) break;

                for (var hit : hits) {
                    Map<String, Object> source = hit.source();
                    if (source != null && source.get("resourceId") != null) {
                        Long rid = ((Number) source.get("resourceId")).longValue();
                        ids.add(rid);
                        lastResourceId = rid;
                    }
                }
            }
        } catch (Exception e) {
            log.error("扫描ES resource_text索引失败: docType={}, error={}", docType, e.getMessage(), e);
        }
        return ids;
    }
}