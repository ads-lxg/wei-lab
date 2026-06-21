package com.laboa.search.controller;

import com.laboa.common.result.Result;
import com.laboa.common.search.DocIdValidator;
import com.laboa.search.service.SearchHitVO;
import com.laboa.search.service.SearchResult;
import com.laboa.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final List<DocIdValidator> docIdValidators;

    @GetMapping
    public Result<SearchResult> search(@RequestParam("keyword") String keyword,
                                       @RequestParam(value = "type", defaultValue = "all") String type,
                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                       @RequestParam(value = "size", defaultValue = "10") int size,
                                       @RequestParam(value = "searchMode", defaultValue = "hybrid") String searchMode) {
        // 取较大候选窗口，在内存中完成过滤和分页
        int candidateSize = Math.max(size * 5, 100);
        SearchResult searchResult = searchService.search(keyword, type, 1, candidateSize, null, searchMode);

        if (searchResult.getHits() == null || searchResult.getHits().isEmpty()) {
            return Result.success(searchResult);
        }

        // 按docType分组docIds
        Map<String, Set<String>> docIdsByType = searchResult.getHits().stream()
                .collect(Collectors.groupingBy(
                        h -> h.getDocType() != null ? h.getDocType() : "unknown",
                        Collectors.mapping(SearchHitVO::getDocId, Collectors.toSet())
                ));

        // 使用各模块的validator校验docId有效性
        Set<String> validDocIds = new HashSet<>();
        for (DocIdValidator validator : docIdValidators) {
            Set<String> idsOfType = docIdsByType.get(validator.getDocType());
            if (idsOfType != null && !idsOfType.isEmpty()) {
                validDocIds.addAll(validator.filterValidDocIds(idsOfType));
            }
        }

        // 过滤掉无效的搜索结果
        List<SearchHitVO> filteredHits = searchResult.getHits().stream()
                .filter(h -> validDocIds.contains(h.getDocId()))
                .collect(Collectors.toList());

        // 重新分页
        int from = (page - 1) * size;
        int endIdx = Math.min(from + size, filteredHits.size());
        List<SearchHitVO> pageHits = from < filteredHits.size()
                ? filteredHits.subList(from, endIdx)
                : List.of();

        SearchResult result = new SearchResult(filteredHits.size(), pageHits);
        return Result.success(result);
    }
}
