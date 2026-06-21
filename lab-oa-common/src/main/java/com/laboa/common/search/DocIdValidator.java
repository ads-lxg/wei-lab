package com.laboa.common.search;

import java.util.Set;

/**
 * 文档ID有效性校验器 — 用于过滤ES搜索结果中已删除的文档
 * 各业务模块（literature/doc）实现此接口，搜索模块通过Spring注入所有实现
 * 来过滤掉ES中残留的已删除文档
 */
public interface DocIdValidator {

    /**
     * 返回此校验器处理的文档类型（如 "literature", "doc"）
     */
    String getDocType();

    /**
     * 从给定的docId集合中筛选出仍然存在（未删除）的docId
     * @param docIds 待校验的文档ID集合
     * @return 有效的文档ID集合
     */
    Set<String> filterValidDocIds(Set<String> docIds);
}
