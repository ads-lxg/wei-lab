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
import java.util.HashMap;
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
                return;
            }

            esClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("resourceId", p -> p.long_(l -> l))
                            .properties("title", p -> p.text(t -> t.analyzer("standard")))
                            .properties("docType", p -> p.keyword(k -> k))
                            .properties("fileType", p -> p.keyword(k -> k))
                            .properties("text", p -> p.text(t -> t.analyzer("ik_max_word")))
                            .properties("createdAt", p -> p.date(d -> d))
                    )
            ));
            log.info("ES索引 '{}' 创建成功", INDEX_NAME);
        } catch (ElasticsearchException | IOException e) {
            log.error("创建ES索引失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void indexText(Long resourceId, String docType, String title,
                          String fileType, String text) {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过ES索引: resourceId={}, docType={}", resourceId, docType);
            return;
        }

        try {
            // 截断过长文本（ES 单字段建议不超过 10MB）
            String truncated = text.length() > 10_000_000 ? text.substring(0, 10_000_000) : text;

            Map<String, Object> doc = new HashMap<>();
            doc.put("resourceId", resourceId);
            doc.put("title", title);
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

            log.info("ES文本索引成功: resourceId={}, docType={}, textLength={}",
                    resourceId, docType, truncated.length());
        } catch (ElasticsearchException | IOException e) {
            log.error("ES文本索引失败: resourceId={}, docType={}, error={}",
                    resourceId, docType, e.getMessage(), e);
        }
    }

    @Override
    public void deleteByResourceId(Long resourceId, String docType) {
        try {
            String esId = docType + "_" + resourceId;

            // 使用 DeleteRequest，文档不存在也不报错
            esClient.delete(DeleteRequest.of(d -> d
                    .index(INDEX_NAME)
                    .id(esId)
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
}