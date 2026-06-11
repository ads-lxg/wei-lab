package com.laboa.rag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天消息实体
 */
@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话UUID */
    private String sessionId;

    /** 角色: user / assistant / system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 引用来源JSON */
    private String citationsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
