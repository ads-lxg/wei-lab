package com.laboa.search.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.laboa.common.result.Result;
import com.laboa.search.service.SearchResult;
import com.laboa.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final ElasticsearchClient esClient;

    @GetMapping
    public Result<SearchResult> search(@RequestParam("keyword") String keyword,
                                       @RequestParam(value = "type", defaultValue = "all") String type,
                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                       @RequestParam(value = "size", defaultValue = "10") int size) {
        SearchResult searchResult = searchService.search(keyword, type, page, size);
        return Result.success(searchResult);
    }

    /**
     * ES 索引状态查询 — 查看 resource_text 和 doc_chunks 的文档数
     */
    @GetMapping("/es-status")
    public Result<Map<String, Object>> esStatus() {
        Map<String, Object> status = new HashMap<>();
        for (String index : new String[]{"resource_text", "doc_chunks", "literature", "md_documents"}) {
            try {
                boolean exists = esClient.indices().exists(e -> e.index(index)).value();
                if (exists) {
                    long count = esClient.count(c -> c.index(index)).count();
                    status.put(index, Map.of("exists", true, "count", count));
                } else {
                    status.put(index, Map.of("exists", false, "count", 0));
                }
            } catch (IOException e) {
                status.put(index, Map.of("exists", false, "count", 0, "error", e.getMessage()));
            }
        }
        return Result.success(status);
    }
}