-- ============================================================
-- 为所有使用逻辑删除的表添加 deleted_time 列
-- 配合 MetaObjectHandler，软删除时自动记录删除时间
-- 配合定时任务 CleanupJob，自动清理超过10天的已删除数据
-- 兼容 MySQL 5.7+
-- ============================================================

DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;

DELIMITER //
CREATE PROCEDURE `add_column_if_not_exists`(
    IN tableName VARCHAR(128),
    IN columnName VARCHAR(128),
    IN columnDef VARCHAR(512)
)
BEGIN
    DECLARE colCount INT DEFAULT 0;
    SELECT COUNT(*) INTO colCount
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tableName
      AND COLUMN_NAME = columnName;
    IF colCount = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', tableName, '` ADD COLUMN `', columnName, '` ', columnDef);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 有 update_time 的表（直接加 deleted_time 即可）
CALL add_column_if_not_exists('sys_user', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");
CALL add_column_if_not_exists('sys_role', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");
CALL add_column_if_not_exists('sys_permission', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");
CALL add_column_if_not_exists('security_question', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");
CALL add_column_if_not_exists('literature', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");
CALL add_column_if_not_exists('rag_folder', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");
CALL add_column_if_not_exists('md_document', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");
CALL add_column_if_not_exists('chat_session', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");

-- 无 update_time 的表（先加 update_time，再加 deleted_time）
CALL add_column_if_not_exists('notification', 'update_time', "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");
CALL add_column_if_not_exists('notification', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");

CALL add_column_if_not_exists('minio_file', 'update_time', "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");
CALL add_column_if_not_exists('minio_file', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");

DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;
