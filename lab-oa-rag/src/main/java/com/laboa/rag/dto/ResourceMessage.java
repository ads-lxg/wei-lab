package com.laboa.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RabbitMQ 消息体 — 资源删除/解析事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceMessage {

    /** 业务主键 */
    private Long resourceId;

    /** 文档类型: literature / doc */
    private String docType;

    /** MinIO 文件ID（删除时需要） */
    private Long fileId;

    /** 文件名（解析时需要） */
    private String fileName;
}