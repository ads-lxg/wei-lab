-- 修复 sys_log 表缺失 request_params 列的问题
-- 适用于已存在 sys_log 表但缺少 request_params 列的数据库
-- 兼容 MySQL 5.7+（不使用 IF NOT EXISTS）

-- 使用存储过程安全添加列（列已存在则跳过）
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

CALL add_column_if_not_exists('sys_log', 'request_params', "text COMMENT '请求参数' AFTER `request_url`");

DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;
