package com.laboa.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文档删除事件 — 删除文献/文档后发布，RAG 模块监听并同步删除 ES chunks
 */
@Getter
public class DocumentDeletedEvent extends ApplicationEvent {

    private final String docType;   // literature / doc
    private final Long docId;       // 文档ID

    public DocumentDeletedEvent(Object source, String docType, Long docId) {
        super(source);
        this.docType = docType;
        this.docId = docId;
    }
}
