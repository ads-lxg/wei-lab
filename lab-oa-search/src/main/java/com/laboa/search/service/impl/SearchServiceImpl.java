package com.laboa.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import com.laboa.common.exception.BusinessException;
import com.laboa.search.service.SearchHitVO;
import com.laboa.search.service.SearchResult;
import com.laboa.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public SearchResult search(String keyword, String type, int page, int size) {
        List<String> indices = resolveIndices(type);
        int from = (page - 1) * size;

        try {
            List<SearchHitVO> allHits = new ArrayList<>();
            long total = 0;

            for (String index : indices) {
                SearchRequest searchRequest = SearchRequest.of(s -> s
                        .index(index)
                        .from(0)
                        .size(size)
                        .query(q -> q
                                .bool(b -> b
                                        .should(sq -> sq.match(m -> m
                                                .field("title")
                                                .query(keyword)
                                        ))
                                        .should(sq -> sq.match(m -> m
                                                .field("text")
                                                .query(keyword)
                                        ))
                                        .should(sq -> sq.match(m -> m
                                                .field("plainText")
                                                .query(keyword)
                                        ))
                                        .should(sq -> sq.match(m -> m
                                                .field("abstract")
                                                .query(keyword)
                                        ))
                                        .minimumShouldMatch("1")
                                )
                        )
                        .highlight(h -> h
                                .fields("title", HighlightField.of(f -> f))
                                .fields("text", HighlightField.of(f -> f))
                                .fields("plainText", HighlightField.of(f -> f))
                                .fields("abstract", HighlightField.of(f -> f))
                        )
                );

                SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

                if (response.hits().total() != null) {
                    total += response.hits().total().value();
                }

                // 将索引名映射为前端可读的类型
                String displayType = mapIndexToType(index);

                for (Hit<Map> hit : response.hits().hits()) {
                    SearchHitVO vo = new SearchHitVO();
                    Map<String, Object> source = hit.source();
                    if (source != null) {
                        // resourceId 优先，其次 id
                        vo.setDocId(String.valueOf(source.getOrDefault("resourceId",
                                source.getOrDefault("id", ""))));
                        Object title = source.get("title");
                        vo.setTitle(title != null ? String.valueOf(title) : "");
                        // 优先读取 ES 内部的 docType 字段，否则用索引名映射
                        Object docType = source.get("docType");
                        vo.setType(docType != null ? String.valueOf(docType) : displayType);
                    }
                    vo.setScore((double) hit.score());

                    Map<String, List<String>> highlightMap = hit.highlight();
                    if (highlightMap != null && !highlightMap.isEmpty()) {
                        String highlightText = highlightMap.values().stream()
                                .flatMap(List::stream)
                                .collect(Collectors.joining(" ... "));
                        vo.setHighlight(highlightText);
                    }

                    allHits.add(vo);
                }
            }

            // 按 score 排序并截取分页
            allHits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            int endIdx = Math.min(from + size, allHits.size());
            List<SearchHitVO> pageHits = from < allHits.size() ? allHits.subList(from, endIdx) : Collections.emptyList();

            return new SearchResult(total, pageHits);
        } catch (ElasticsearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.warn("ES 索引尚未创建，搜索降级返回空结果 (keyword={})", keyword);
                return new SearchResult(0, Collections.emptyList());
            }
            log.warn("ES 查询异常: keyword={}, type={}, msg={}", keyword, type, e.getMessage());
            throw new BusinessException("搜索服务暂不可用，请稍后重试");
        } catch (Exception e) {
            log.warn("搜索服务不可用: keyword={}, type={}, msg={}", keyword, type, e.getMessage());
            throw new BusinessException("搜索服务暂不可用，请稍后重试");
        }
    }

    /**
     * 解析搜索的索引列表
     * 只搜索 resource_text（全文检索），不再搜索 literature/md_documents
     * 元数据搜索（标题、作者等）已在 MySQL 中通过 MyBatis-Plus 实现
     */
    private List<String> resolveIndices(String type) {
        // 所有类型统一搜索 resource_text 索引
        return List.of("resource_text");
    }

    /**
     * 将 ES 索引名映射为前端类型
     */
    private String mapIndexToType(String index) {
        return switch (index) {
            case "md_documents" -> "doc";
            case "literature" -> "literature";
            case "resource_text" -> "fulltext";
            default -> index;
        };
    }
}