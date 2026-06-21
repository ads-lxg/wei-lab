package com.laboa.rag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天会话实体
 */
@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话UUID */
    private String sessionId;

    /** 用户ID */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 压缩后的对话摘要 */
    private String summary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 逻辑删除时间（定时清理任务根据此字段判断） */
    private LocalDateTime deletedTime;
}
