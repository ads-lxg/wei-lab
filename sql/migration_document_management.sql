-- =============================================
-- 文献管理模块 - 迁移SQL
-- 功能：
--   1. literature表增加 folder_id 字段（关联目录树）
--   2. literature表增加 file_name 字段（存储原始文件名）
--   3. 添加对应索引
-- 执行前请备份数据！
-- 使用存储过程安全添加列和索引（已存在则跳过）
-- =============================================

-- 安全添加列和索引的存储过程
DROP PROCEDURE IF EXISTS `add_literature_mgmt_columns`;

DELIMITER $$
CREATE PROCEDURE `add_literature_mgmt_columns`()
BEGIN
    -- 添加 folder_id 列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature' AND COLUMN_NAME = 'folder_id'
    ) THEN
        ALTER TABLE `literature` ADD COLUMN `folder_id` BIGINT NULL COMMENT '所属目录ID，关联rag_folder.id' AFTER `doi`;
    END IF;

    -- 添加 file_name 列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature' AND COLUMN_NAME = 'file_name'
    ) THEN
        ALTER TABLE `literature` ADD COLUMN `file_name` VARCHAR(500) NULL COMMENT '原始文件名' AFTER `file_type`;
    END IF;

    -- 添加 idx_folder_id 索引
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature' AND INDEX_NAME = 'idx_folder_id'
    ) THEN
        ALTER TABLE `literature` ADD INDEX `idx_folder_id` (`folder_id`);
    END IF;

    -- 添加 idx_file_name 索引
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature' AND INDEX_NAME = 'idx_file_name'
    ) THEN
        ALTER TABLE `literature` ADD INDEX `idx_file_name` (`file_name`(255));
    END IF;
END$$
DELIMITER ;

-- 执行
CALL `add_literature_mgmt_columns`();

-- 清理存储过程
DROP PROCEDURE IF EXISTS `add_literature_mgmt_columns`;
