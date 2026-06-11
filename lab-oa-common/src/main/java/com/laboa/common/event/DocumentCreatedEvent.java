package com.laboa.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文档创建事件 — 上传文献/文档后发布，RAG 模块监听并自动入库
 * 携带文件字节，避免从 MinIO 回下载
 */
@Getter
public class DocumentCreatedEvent extends ApplicationEvent {

    private final String docType;       // literature / doc
    private final Long docId;           // 文档ID
    private final Long fileId;          // MinIO 文件ID
    private final String fileName;      // 文件名
    private final boolean ragSource;    // 是否作为RAG来源
    private final byte[] fileContent;   // 文件字节（直接解析，无需回下载）

    public DocumentCreatedEvent(Object source, String docType, Long docId, Long fileId,
                                String fileName, boolean ragSource, byte[] fileContent) {
        super(source);
        this.docType = docType;
        this.docId = docId;
        this.fileId = fileId;
        this.fileName = fileName;
        this.ragSource = ragSource;
        this.fileContent = fileContent;
    }
}