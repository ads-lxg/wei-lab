package com.laboa.search.controller;

import com.laboa.common.result.Result;
import com.laboa.search.service.SearchResult;
import com.laboa.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public Result<SearchResult> search(@RequestParam("keyword") String keyword,
                                       @RequestParam(value = "type", defaultValue = "all") String type,
                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                       @RequestParam(value = "size", defaultValue = "10") int size) {
        SearchResult searchResult = searchService.search(keyword, type, page, size);
        return Result.success(searchResult);
    }
}