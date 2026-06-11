package com.laboa.rag.dto;

import lombok.Data;

/**
 * 聊天请求DTO
 */
@Data
public class ChatRequest {
    /** 会话ID，为空则创建新会话 */
    private String sessionId;

    /** 用户消息 */
    private String message;
}
