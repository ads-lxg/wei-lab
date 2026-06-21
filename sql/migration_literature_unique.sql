-- 文献去重索引：为 DOI 和文件名添加索引，提升去重查询性能
-- 注意：由于使用逻辑删除(deleted字段)，无法使用唯一索引
-- 去重约束由代码层面(checkDuplicate方法)保证
-- 兼容 MySQL 5.7+（索引已存在时跳过）

-- 使用存储过程安全添加索引
DROP PROCEDURE IF EXISTS `add_index_if_not_exists`;

DELIMITER //
CREATE PROCEDURE `add_index_if_not_exists`(
    IN tableName VARCHAR(128),
    IN indexName VARCHAR(128),
    IN indexDef VARCHAR(512)
)
BEGIN
    DECLARE idxCount INT DEFAULT 0;
    SELECT COUNT(*) INTO idxCount
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tableName
      AND INDEX_NAME = indexName;
    IF idxCount = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', tableName, '` ADD INDEX `', indexName, '` ', indexDef);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- DOI索引（提升按DOI查重的查询速度）
CALL add_index_if_not_exists('literature', 'idx_doi', '(`doi`)');

-- 文件名索引（提升按文件名查重的查询速度）
CALL add_index_if_not_exists('literature', 'idx_file_name', '(`file_name`(255))');

DROP PROCEDURE IF EXISTS `add_index_if_not_exists`;
