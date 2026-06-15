package com.laboa.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统操作日志
 */
@Data
@TableName("sys_log")
public class SysLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作用户ID */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 操作人真实姓名 */
    private String realName;

    /** 操作模块（如: 文献管理、用户管理、系统配置） */
    private String module;

    /** 操作类型（如: 新增、修改、删除、导出、登录、注销） */
    private String action;

    /** 操作目标 */
    private String target;

    /** 目标ID */
    private Long targetId;

    /** 请求方法 */
    private String requestMethod;

    /** 请求URL */
    private String requestUrl;

    /** 请求参数 */
    private String requestParams;

    /** 操作结果（SUCCESS / FAIL） */
    private String result;

    /** 错误信息 */
    private String errorMsg;

    /** 操作耗时(ms) */
    private Long costTime;

    /** 操作IP */
    private String ip;

    /** 操作时间 */
    private LocalDateTime createTime;
}
