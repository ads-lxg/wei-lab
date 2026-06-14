-- 系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT NOT NULL,
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `description` VARCHAR(255) COMMENT '描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置';

-- 初始配置
INSERT INTO `sys_config` (`id`, `config_key`, `config_value`, `description`) VALUES
(1, 'site_name', '魏大鹏课题组文献阅读室', '网站名称'),
(2, 'site_logo', '/title.png', '网站图标路径')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- 添加系统配置权限
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `parent_id`, `type`, `sort`) VALUES
(200, 'system:config', '系统配置管理', 2, 'api', 50)
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- 给管理员角色添加系统配置权限
INSERT IGNORE INTO `role_permission` (`role_id`, `perm_id`) VALUES (1, 200);

-- 移除游客的RAG问答权限（游客不应访问RAG问答、对话历史、知识储备）
DELETE FROM `role_permission` WHERE `role_id` = 4 AND `perm_id` IN (18, 19, 20, 21, 22);
