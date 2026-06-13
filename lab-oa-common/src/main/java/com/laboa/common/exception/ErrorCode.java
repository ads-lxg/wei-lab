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
    FOLDER_MOVE_ERROR(4008, "目录移动失败"),

    /* ---- 文献管理模块 5xxx ---- */
    DOCUMENT_NOT_EXIST(5001, "文献不存在"),
    DOCUMENT_ALREADY_DELETED(5002, "文献已在回收站中"),
    DOCUMENT_NOT_IN_RECYCLE(5003, "文献不在回收站中，无法恢复"),
    UPLOAD_ERROR(5004, "文献上传失败"),
    DOWNLOAD_ERROR(5005, "文献下载失败"),
    MOVE_ERROR(5006, "文献移动失败"),
    BATCH_UPLOAD_ERROR(5007, "批量上传失败"),
    DOCUMENT_FOLDER_NOT_EXIST(5008, "所属目录不存在"),
    DOCUMENT_BATCH_EMPTY(5009, "批量操作列表不能为空"),
    DOCUMENT_FILE_MISSING(5010, "文献关联文件不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
