package com.laboa.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表 — 保证数据库操作和 MQ 消息发送的事务一致性
 */
@Data
@TableName("outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的资源ID（literature.id 或 md_document.id） */
    private Long aggregateId;

    /** 事件类型: DELETE_RESOURCE / PARSE_RESOURCE */
    private String eventType;

    /** JSON 消息体 */
    private String payload;

    /** 投递状态: PENDING / SENT / FAILED */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 文档类型: literature / doc */
    private String docType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}