-- 修复逻辑删除后邮箱唯一索引冲突的问题
-- 将 uk_email 从 (email) 改为 (email, deleted)，使已删除用户的邮箱不再占用唯一约束
-- 兼容 MySQL 5.7+（索引已修复则跳过）

-- 使用存储过程检查索引并对旧索引进行迁移
DROP PROCEDURE IF EXISTS `migrate_email_unique`;

DELIMITER //
CREATE PROCEDURE `migrate_email_unique`()
BEGIN
    DECLARE idxColumnCount INT DEFAULT 0;

    -- 检查 uk_email 当前是单列还是双列
    SELECT COUNT(*) INTO idxColumnCount
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND INDEX_NAME = 'uk_email';

    -- 只有单列(email)时=1，双列(email,deleted)时=2
    IF idxColumnCount = 1 THEN
        ALTER TABLE `sys_user` DROP INDEX `uk_email`;
        ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_email` (`email`, `deleted`);
    END IF;
END //
DELIMITER ;

CALL migrate_email_unique();

DROP PROCEDURE IF EXISTS `migrate_email_unique`;
