package com.laboa.common.constant;

/**
 * 系统常量
 */
public interface Constants {

    String TRACE_ID = "traceId";

    int STATUS_NORMAL = 1;
    int STATUS_DISABLED = 0;

    int DOC_STATUS_DRAFT = 1;
    int DOC_STATUS_PUBLISHED = 2;
    int DOC_STATUS_ARCHIVED = 3;

    int FILE_STATUS_TEMP = 1;
    int FILE_STATUS_FORMAL = 2;

    int PERMISSION_VIEW = 1;
    int PERMISSION_DOWNLOAD = 2;

    String ROLE_ADMIN = "admin";
    String ROLE_TEACHER = "teacher";
    String ROLE_STUDENT = "student";
    String ROLE_GUEST = "guest";
}