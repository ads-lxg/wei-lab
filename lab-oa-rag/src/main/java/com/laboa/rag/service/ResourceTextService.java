package com.laboa.rag.service;

/**
 * ES 纯文本索引服务 — 存储 Tika 解析后的正文，用于全文检索
 */
public interface ResourceTextService {

    /** 创建 resource_text 索引（若不存在） */
    void createIndexIfNotExists();

    /**
     * 索引一篇文档的Tika解析纯文本到 ES
     * 若已存在（相同 resourceId + docType）则覆盖更新（幂等）
     */
    void indexText(Long resourceId, String docType, String title,
                   String fileType, String text);

    /**
     * 根据 resourceId 和 docType 从 ES 中删除
     * 文档不存在不报错（幂等）
     */
    void deleteByResourceId(Long resourceId, String docType);
}