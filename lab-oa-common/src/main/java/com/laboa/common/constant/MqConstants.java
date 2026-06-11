package com.laboa.common.constant;

/**
 * RabbitMQ 常量定义
 */
public final class MqConstants {

    private MqConstants() {}

    /** 删除资源队列 */
    public static final String DELETE_QUEUE = "resource.delete.queue";

    /** 删除死信队列 */
    public static final String DELETE_DLQ = "resource.delete.dlq";

    /** 删除交换机 */
    public static final String DELETE_EXCHANGE = "resource.delete.exchange";

    /** 删除路由键 */
    public static final String DELETE_ROUTING_KEY = "resource.delete";

    /** Outbox 事件类型 */
    public static final String EVENT_DELETE_RESOURCE = "DELETE_RESOURCE";
    public static final String EVENT_PARSE_RESOURCE = "PARSE_RESOURCE";

    /** Outbox 事件状态 */
    public static final String OUTBOX_STATUS_PENDING = "PENDING";
    public static final String OUTBOX_STATUS_SENT = "SENT";
    public static final String OUTBOX_STATUS_FAILED = "FAILED";

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 文档类型 */
    public static final String DOC_TYPE_LITERATURE = "literature";
    public static final String DOC_TYPE_DOC = "doc";
}