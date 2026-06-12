-- =============================================
-- 文献目录树管理模块 - 建表SQL
-- =============================================

CREATE TABLE rag_folder
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父目录ID',
    folder_name VARCHAR(200) NOT NULL COMMENT '目录名称',
    path        VARCHAR(1000) NOT NULL COMMENT '目录路径',
    level_no    INT          NOT NULL DEFAULT 1 COMMENT '目录层级',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    INDEX idx_parent_id (parent_id),
    INDEX idx_path (path(255))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文献目录表';

-- 初始化根节点示例（可选，也可通过接口动态创建）
-- INSERT INTO rag_folder (id, parent_id, folder_name, path, level_no, sort_order) VALUES (1, 0, 'AI', '/1', 1, 0);
