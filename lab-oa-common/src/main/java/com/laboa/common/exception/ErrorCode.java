package com.laboa.common.exception;

import lombok.Getter;

/**
 * 业务错误码枚举
 */
@Getter
public enum ErrorCode {

    /* ---- 目录模块 4xxx ---- */
    FOLDER_NOT_EXIST(4001, "目录不存在"),
    FOLDER_NAME_REPEAT(4002, "同级目录下名称不可重复"),
    FOLDER_HAS_CHILDREN(4003, "目录下存在子目录，禁止删除"),
    FOLDER_MOVE_SELF(4004, "禁止移动到自身下面"),
    FOLDER_MOVE_TO_CHILD(4005, "禁止移动到自己的子节点下面"),
    FOLDER_PARENT_NOT_EXIST(4006, "父目录不存在"),
    FOLDER_MOVE_TARGET_NOT_EXIST(4007, "目标目录不存在"),
    FOLDER_MOVE_ERROR(4008, "目录移动失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
