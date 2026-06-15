package com.laboa.system.dto;

import lombok.Data;

/**
 * 操作日志查询DTO
 */
@Data
public class SysLogSearchDTO {

    private Integer page = 1;

    private Integer size = 10;

    /** 操作人（模糊匹配用户名或真实姓名） */
    private String operator;

    /** 操作类型（精确匹配） */
    private String action;

    /** 操作目标（模糊匹配） */
    private String target;

    /** 操作模块（精确匹配） */
    private String module;

    /** 操作结果（SUCCESS / FAIL） */
    private String result;

    /** 操作IP（模糊匹配） */
    private String ip;

    /** 开始时间（yyyy-MM-dd HH:mm:ss） */
    private String startTime;

    /** 结束时间（yyyy-MM-dd HH:mm:ss） */
    private String endTime;

    /** 排序字段（默认 createTime） */
    private String sortField;

    /** 排序方向（asc/desc，默认 desc） */
    private String sortOrder;
}
