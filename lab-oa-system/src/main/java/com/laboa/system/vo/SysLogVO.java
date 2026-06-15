package com.laboa.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志VO
 */
@Data
public class SysLogVO {

    private Long id;

    private Long userId;

    private String username;

    private String realName;

    /** 操作模块 */
    private String module;

    /** 操作类型 */
    private String action;

    /** 操作目标 */
    private String target;

    /** 目标ID */
    private Long targetId;

    /** 请求方法 */
    private String requestMethod;

    /** 请求URL */
    private String requestUrl;

    /** 操作结果 */
    private String result;

    /** 操作耗时(ms) */
    private Long costTime;

    /** 操作IP */
    private String ip;

    /** 操作时间 */
    private LocalDateTime createTime;
}
