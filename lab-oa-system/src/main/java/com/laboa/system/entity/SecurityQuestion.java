package com.laboa.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("security_question")
public class SecurityQuestion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String question;

    private String answer;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 逻辑删除时间（定时清理任务根据此字段判断） */
    private LocalDateTime deletedTime;
}
