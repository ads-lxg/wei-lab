package com.laboa.search.service;

import java.util.Set;

public interface SearchService {

    SearchResult search(String keyword, String type, int page, int size);

    /**
     * 搜索（支持排除已删除的文档ID）
     */
    SearchResult search(String keyword, String type, int page, int size, Set<String> excludeDocIds);

    /**
     * 搜索（支持搜索模式）
     * @param mode 搜索模式: "bm25"=BM25关键词优先, "knn"=KNN语义检索优先, 默认"hybrid"均衡混合
     */
    SearchResult search(String keyword, String type, int page, int size, Set<String> excludeDocIds, String mode);
}