-- =====================================================
-- Lab OA 实验室办公系统 - 初始化 SQL
-- 兼容 MySQL 8.0
-- =====================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE DATABASE IF NOT EXISTS lab_oa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE lab_oa;

-- =====================================================
-- 用户与权限管理
-- =====================================================

DROP TABLE IF EXISTS `role_permission`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `sys_permission`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `minio_file`;
DROP TABLE IF EXISTS `md_document`;
DROP TABLE IF EXISTS `literature`;
DROP TABLE IF EXISTS `download_log`;
DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;
DROP TABLE IF EXISTS `outbox_event`;

CREATE TABLE `sys_user` (
    `id` bigint NOT NULL,
    `username` varchar(50) NOT NULL,
    `password` varchar(200) NOT NULL,
    `email` varchar(100) NOT NULL,
    `phone` varchar(20) DEFAULT NULL,
    `wechat_openid` varchar(100) DEFAULT NULL,
    `real_name` varchar(50) DEFAULT NULL,
    `avatar` varchar(500) DEFAULT NULL,
    `status` tinyint DEFAULT 1 COMMENT '1:正常 0:禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint DEFAULT 0 COMMENT '0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE `sys_role` (
    `id` bigint NOT NULL,
    `role_code` varchar(50) NOT NULL,
    `role_name` varchar(50) NOT NULL,
    `description` varchar(200) DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint DEFAULT 0 COMMENT '0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

CREATE TABLE `sys_permission` (
    `id` bigint NOT NULL,
    `perm_code` varchar(100) NOT NULL,
    `perm_name` varchar(100) NOT NULL,
    `parent_id` bigint DEFAULT 0,
    `type` varchar(20) NOT NULL COMMENT 'menu:菜单 button:按钮 api:接口',
    `path` varchar(200) DEFAULT NULL,
    `icon` varchar(100) DEFAULT NULL,
    `sort` int DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint DEFAULT 0 COMMENT '0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

CREATE TABLE `user_role` (
    `user_id` bigint NOT NULL,
    `role_id` bigint NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE `role_permission` (
    `role_id` bigint NOT NULL,
    `perm_id` bigint NOT NULL,
    PRIMARY KEY (`role_id`, `perm_id`),
    KEY `idx_perm_id` (`perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- =====================================================
-- MinIO 文件记录表
-- =====================================================

CREATE TABLE `minio_file` (
    `id` bigint NOT NULL,
    `original_name` varchar(500) NOT NULL,
    `stored_name` varchar(500) NOT NULL COMMENT 'MinIO 对象名',
    `bucket` varchar(100) NOT NULL,
    `file_path` varchar(500) DEFAULT NULL,
    `file_size` bigint DEFAULT 0,
    `mime_type` varchar(100) DEFAULT NULL,
    `md5` varchar(32) DEFAULT NULL,
    `uploader_id` bigint DEFAULT NULL,
    `status` tinyint DEFAULT 1 COMMENT '1:临时 2:正式',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` tinyint DEFAULT 0 COMMENT '0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_uploader_id` (`uploader_id`),
    KEY `idx_md5` (`md5`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MinIO文件记录表';

-- =====================================================
-- 内部技术文档表
-- =====================================================

CREATE TABLE `md_document` (
    `id` bigint NOT NULL,
    `title` varchar(200) NOT NULL,
    `file_id` bigint DEFAULT NULL COMMENT '关联 minio_file.id',
    `file_type` varchar(20) DEFAULT NULL COMMENT '文件类型: pdf/docx/md/txt',
    `author_id` bigint DEFAULT NULL COMMENT '作者用户ID',
    `status` tinyint DEFAULT 1 COMMENT '1:草稿 2:发布 3:归档',
    `rag_source` tinyint DEFAULT 1 COMMENT '是否RAG来源: 内部文档默认1',
    `parse_status` varchar(20) DEFAULT 'NONE' COMMENT 'NONE/PENDING/SUCCESS/FAILED',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint DEFAULT 0 COMMENT '0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_title` (`title`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    FOREIGN KEY (`file_id`) REFERENCES `minio_file`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内部技术文档表';

-- =====================================================
-- 文献表
-- =====================================================

CREATE TABLE `literature` (
    `id` bigint NOT NULL,
    `title` varchar(300) NOT NULL,
    `authors` varchar(500) DEFAULT NULL,
    `abstract` varchar(2000) DEFAULT NULL COMMENT '摘要，限制2000字符以内',
    `keywords` varchar(500) DEFAULT NULL,
    `publish_date` date DEFAULT NULL COMMENT '发表日期',
    `source_journal` varchar(300) DEFAULT NULL COMMENT '来源期刊',
    `doi` varchar(200) DEFAULT NULL,
    `file_id` bigint DEFAULT NULL COMMENT '关联 minio_file.id',
    `file_type` varchar(20) DEFAULT NULL COMMENT '文件类型: pdf/docx/md/txt',
    `uploader_id` bigint DEFAULT NULL COMMENT '上传者用户ID',
    `permission_level` tinyint DEFAULT 1 COMMENT '1:仅查看 2:可下载',
    `rag_source` tinyint DEFAULT 0 COMMENT '是否RAG来源: 0=否 1=是',
    `parse_status` varchar(20) DEFAULT 'NONE' COMMENT 'NONE/PENDING/SUCCESS/FAILED',
    `view_count` int DEFAULT 0,
    `download_count` int DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint DEFAULT 0 COMMENT '0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_title` (`title`),
    KEY `idx_authors` (`authors`(255)),
    KEY `idx_keywords` (`keywords`(255)),
    KEY `idx_publish_date` (`publish_date`),
    KEY `idx_uploader_id` (`uploader_id`),
    KEY `idx_deleted` (`deleted`),
    FOREIGN KEY (`file_id`) REFERENCES `minio_file`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文献表';

-- =====================================================
-- 下载日志表
-- =====================================================

CREATE TABLE `download_log` (
    `id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `literature_id` bigint NOT NULL,
    `download_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_literature_id` (`literature_id`),
    KEY `idx_download_time` (`download_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下载日志表';

-- =====================================================
-- 通知表
-- =====================================================

CREATE TABLE `notification` (
    `id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `title` varchar(200) NOT NULL,
    `content` text,
    `type` varchar(50) NOT NULL COMMENT 'LITERATURE_UPLOAD:文献上传 SYSTEM:系统通知',
    `is_read` tinyint DEFAULT 0 COMMENT '0:未读 1:已读',
    `related_id` bigint DEFAULT NULL COMMENT '关联业务ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` tinyint DEFAULT 0 COMMENT '0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_type` (`type`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

CREATE TABLE `chat_session` (
    `id` bigint NOT NULL,
    `session_id` varchar(36) NOT NULL COMMENT '会话UUID',
    `user_id` bigint NOT NULL,
    `title` varchar(200) DEFAULT NULL COMMENT '会话标题',
    `summary` text DEFAULT NULL COMMENT '压缩后的对话摘要',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint DEFAULT 0 COMMENT '0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

CREATE TABLE `chat_message` (
    `id` bigint NOT NULL,
    `session_id` varchar(36) NOT NULL COMMENT '会话UUID',
    `role` varchar(20) NOT NULL COMMENT 'user/assistant/system',
    `content` text NOT NULL,
    `citations_json` json DEFAULT NULL COMMENT '引用来源JSON',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_session_role` (`session_id`, `role`),
    CONSTRAINT `fk_chat_message_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session`(`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- =====================================================
-- Outbox 本地消息表（保证 MySQL 操作与 MQ 消息的事务一致性）
-- =====================================================

CREATE TABLE `outbox_event` (
    `id` bigint NOT NULL,
    `aggregate_id` bigint NOT NULL COMMENT '资源ID（literature.id 或 md_document.id）',
    `event_type` varchar(50) NOT NULL COMMENT 'DELETE_RESOURCE / PARSE_RESOURCE',
    `payload` text NOT NULL COMMENT 'JSON 消息体',
    `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / FAILED',
    `retry_count` int DEFAULT 0 COMMENT '已重试次数',
    `doc_type` varchar(20) DEFAULT NULL COMMENT 'literature / doc',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_aggregate_id` (`aggregate_id`),
    KEY `idx_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox本地消息表';

-- =====================================================
-- 初始数据
-- =====================================================

INSERT INTO `sys_user` (`id`, `username`, `password`, `email`, `real_name`, `status`) VALUES
(1, 'admin', '$2a$10$mScmBVs31E0r7KXrIWXeROJBAcDVo4RJ1/La6a7hUs9yvGCrTjF6m', 'admin@laboa.com', '系统管理员', 1),
(2, 'teacher', '$2a$10$mScmBVs31E0r7KXrIWXeROJBAcDVo4RJ1/La6a7hUs9yvGCrTjF6m', 'teacher@laboa.com', '张教授', 1),
(3, 'student', '$2a$10$mScmBVs31E0r7KXrIWXeROJBAcDVo4RJ1/La6a7hUs9yvGCrTjF6m', 'student@laboa.com', '李同学', 1),
(4, 'guest', '$2a$10$mScmBVs31E0r7KXrIWXeROJBAcDVo4RJ1/La6a7hUs9yvGCrTjF6m', 'guest@laboa.com', '游客', 1);

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`) VALUES
(1, 'admin', '管理员', '系统管理员，拥有所有权限'),
(2, 'teacher', '教师', '教师角色，可上传下载文献，可访问内部文档'),
(3, 'student', '学生', '学生角色，可上传下载文献，可访问内部文档'),
(4, 'guest', '游客', '游客角色，仅可在线查看文献');

-- ── 完整权限定义 v3 ──
-- 功能权限（menu）= 控制菜单可见性，数据权限（api）= 控制 API 接口访问
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `parent_id`, `type`, `path`, `sort`) VALUES
-- 仪表盘
(1,  'dashboard',            '仪表盘',                 0,  'menu', '/dashboard',                    1),
-- 用户权限管理
(2,  'user:manage',          '用户权限管理',              0,  'menu', '/user',                         2),
(3,  'user:list',            '用户列表（查改删）',          2,  'api',  '/api/admin/user/**',              1),
(4,  'user:role',            '角色管理（增删改）',          2,  'api',  '/api/admin/role/**',              2),
(13, 'user:perm',            '权限分配',                 2,  'api',  '/api/admin/permission/**',         3),
-- 内部文档管理
(5,  'doc:manage',           '内部文档管理',              0,  'menu', '/doc',                          3),
(6,  'doc:crud',             '文档增删改查',              5,  'api',  '/api/doc/**',                     1),
-- 文献管理
(7,  'literature:manage',    '文献管理',                 0,  'menu', '/literature',                    4),
(8,  'literature:upload',    '上传文献（单条）',           7,  'api',  '/api/document/upload',            1),
(25, 'literature:batchUpload','批量上传文献（管理员）',       7,  'api',  '/api/document/batch-upload',      1),
(9,  'literature:download',  '下载文献',                 7,  'api',  '/api/document/*/download',          2),
(10, 'literature:folder',    '目录管理',                 7,  'api',  '/api/rag/folder/**',               3),
(11, 'literature:recycle',   '回收站/恢复/彻底删除',         7,  'api',  '/api/document/recycle-bin',        4),
(12, 'literature:view',      '文献查看/搜索/详情',           7,  'api',  '/api/document/folder',             5),
-- 全局搜索
(16, 'search:manage',        '全局搜索',                 0,  'menu', '/search',                        5),
(17, 'search:api',           '搜索接口',                 16, 'api',  '/api/search/**',                  1),
-- AI问答
(18, 'rag:chat',             'AI问答',                  0,  'menu', '/rag',                           6),
(19, 'rag:session',          '会话管理',                 18, 'api',  '/api/session/**',                 1),
(20, 'rag:stream',           '对话流',                   18, 'api',  '/api/chat/**',                    2),
(21, 'rag:search',           '向量搜索',                 18, 'api',  '/api/search/vector-search',        3),
(22, 'rag:citation',         '引用下载',                 18, 'api',  '/api/citation/**',                4),
-- 通知中心
(23, 'notification',         '通知中心',                 0,  'menu', '/notification',                  7),
(24, 'notification:manage',  '通知管理',                 23, 'api',  '/api/notification/**',            1);

INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4);

-- admin：全部权限
INSERT INTO `role_permission` (`role_id`, `perm_id`) VALUES
(1,1),(1,2),(1,3),(1,4),(1,13),(1,5),(1,6),(1,7),(1,8),(1,25),(1,9),(1,10),(1,11),(1,12),(1,16),(1,17),(1,18),(1,19),(1,20),(1,21),(1,22),(1,23),(1,24);

-- teacher：仪表盘、内部文档、文献（上传/下载/查看/目录）、搜索、AI问答（不含ES验证）、通知
INSERT INTO `role_permission` (`role_id`, `perm_id`) VALUES
(2,1),(2,5),(2,6),(2,7),(2,8),(2,9),(2,10),(2,12),(2,16),(2,17),(2,18),(2,19),(2,20),(2,21),(2,22),(2,23),(2,24);

-- student：仪表盘、内部文档、文献（上传/下载/查看，不含目录管理和回收站）、搜索、AI问答、通知
INSERT INTO `role_permission` (`role_id`, `perm_id`) VALUES
(3,1),(3,5),(3,6),(3,7),(3,8),(3,9),(3,12),(3,16),(3,17),(3,18),(3,19),(3,20),(3,21),(3,22),(3,23),(3,24);

-- guest：仪表盘、文献查看、搜索、AI问答
INSERT INTO `role_permission` (`role_id`, `perm_id`) VALUES
(4,1),(4,7),(4,12),(4,16),(4,17),(4,18),(4,19),(4,20),(4,21),(4,22);