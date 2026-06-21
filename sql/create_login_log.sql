-- 登录日志表
CREATE TABLE IF NOT EXISTS `login_log` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `username` VARCHAR(64) DEFAULT NULL COMMENT '用户名',
    `ip` VARCHAR(64) DEFAULT NULL COMMENT '登录IP',
    `device` VARCHAR(256) DEFAULT NULL COMMENT '设备信息',
    `action` VARCHAR(32) NOT NULL COMMENT '操作类型: LOGIN/LOGOUT/LOGIN_FAIL',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';
