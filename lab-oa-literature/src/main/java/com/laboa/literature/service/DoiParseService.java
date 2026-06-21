package com.laboa.literature.service;

import java.util.List;

/**
 * DOI 解析服务 — 通过 Crossref API 批量查询 DOI 元数据
 */
public interface DoiParseService {

    /**
     * 批量解析 DOI，返回解析结果列表
     *
     * @param dois DOI 号列表
     * @return 解析结果列表（顺序与输入一致）
     */
    List<DoiInfo> parseBatch(List<String> dois);

    /**
     * DOI 元数据信息
     */
    record DoiInfo(
            String doi,
            String title,
            String authors,
            String keywords,
            String abstractText,
            String publishDate,
            String sourceJournal,
            boolean success
    ) {
        /** 未找到时的空结果 */
        public static DoiInfo empty(String doi) {
            return new DoiInfo(doi, "", "", "", "", "", "", false);
        }
    }
}
