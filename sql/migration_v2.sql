-- =====================================================
-- Lab OA 数据库迁移脚本 v2
-- 执行方式: mysql -u root -p lab_oa < migration_v2.sql
-- 或在 Navicat/DBeaver 中全选执行
-- 注意：如果某个列/表/约束已存在，对应语句会报错，
-- 这是正常的，不影响其他语句，忽略即可。
-- =====================================================

USE lab_oa;

-- 1. literature 表增加 rag_source、parse_status、file_type 列
ALTER TABLE literature ADD COLUMN rag_source TINYINT DEFAULT 0 COMMENT '是否RAG来源: 0=否 1=是' AFTER permission_level;
ALTER TABLE literature ADD COLUMN parse_status VARCHAR(20) DEFAULT 'NONE' COMMENT '解析状态: NONE/PENDING/SUCCESS/FAILED' AFTER rag_source;
ALTER TABLE literature ADD COLUMN file_type VARCHAR(20) DEFAULT NULL COMMENT '文件类型: pdf/docx/md/txt' AFTER file_id;

-- 2. md_document 表增加 rag_source、parse_status、file_type 列
ALTER TABLE md_document ADD COLUMN rag_source TINYINT DEFAULT 1 COMMENT '是否RAG来源: 内部文档默认1' AFTER status;
ALTER TABLE md_document ADD COLUMN parse_status VARCHAR(20) DEFAULT 'NONE' COMMENT '解析状态: NONE/PENDING/SUCCESS/FAILED' AFTER rag_source;
ALTER TABLE md_document ADD COLUMN file_type VARCHAR(20) DEFAULT NULL COMMENT '文件类型: pdf/docx/md/txt' AFTER file_id;

-- 3. 创建 outbox_event 本地消息表
CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT NOT NULL,
    aggregate_id BIGINT NOT NULL COMMENT '资源ID',
    event_type VARCHAR(50) NOT NULL COMMENT 'DELETE_RESOURCE/PARSE_RESOURCE',
    payload TEXT NOT NULL COMMENT 'JSON消息体',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    doc_type VARCHAR(20) DEFAULT NULL COMMENT 'literature/doc',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_aggregate_id (aggregate_id),
    KEY idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox本地消息表';

-- 4. chat_message 增加外键约束（级联删除）
ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(session_id) ON DELETE CASCADE;

-- 5. 删除已废弃的 rag_conversation 表
DROP TABLE IF EXISTS rag_conversation;