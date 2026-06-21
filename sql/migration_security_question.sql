-- 安全问题题库表
-- 如果表不存在则创建，如果已存在则补充缺失列
-- 兼容 MySQL 5.7+

CREATE TABLE IF NOT EXISTS `security_question` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `question` VARCHAR(512) NOT NULL COMMENT '安全问题',
    `answer` VARCHAR(256) NOT NULL COMMENT '答案',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    `deleted_time` DATETIME DEFAULT NULL COMMENT '逻辑删除时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全问题题库';

-- 使用存储过程安全补充缺失列（列已存在则跳过）
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

CALL add_column_if_not_exists('security_question', 'status', "TINYINT DEFAULT 1 COMMENT '状态: 1启用 0禁用'");
CALL add_column_if_not_exists('security_question', 'update_time', "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");
CALL add_column_if_not_exists('security_question', 'deleted_time', "DATETIME DEFAULT NULL COMMENT '逻辑删除时间'");

DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;

-- 插入默认安全问题（IGNORE 避免重复插入）
INSERT IGNORE INTO `security_question` (`id`, `question`, `answer`) VALUES
(1, '你最喜欢的颜色是什么？', '蓝色'),
(2, '你第一只宠物的名字是什么？', '小黄'),
(3, '你出生的城市是哪里？', '北京'),
(4, '你小时候的绰号是什么？', '小胖'),
(5, '你最喜欢的食物是什么？', '火锅'),
(6, '你的第一个老师姓什么？', '李'),
(7, '你的母亲的姓名是什么？', '张三'),
(8, '你的爱好是什么？', '阅读'),
(9, '你最喜欢的电影是什么？', '肖申克的救赎'),
(10, '你最喜欢的运动是什么？', '篮球');
