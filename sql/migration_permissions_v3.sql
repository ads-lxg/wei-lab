-- =====================================================
-- 权限体系 v3：补全所有模块的功能权限与数据权限
-- 执行前确保 sys_permission 和 role_permission 表存在
-- =====================================================

-- 1. 清空旧权限数据，重新插入完整定义
DELETE FROM role_permission;
DELETE FROM sys_permission;

-- 2. 完整权限定义
-- 功能权限（menu）= 控制菜单可见性
-- 数据权限（api）  = 控制 API 接口访问

INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `parent_id`, `type`, `path`, `sort`) VALUES
-- ====== 首页 ======
(1, 'dashboard',            '首页',             0, 'menu', '/dashboard',              1),

-- ====== 用户权限管理 ======
(2, 'user:manage',          '用户权限管理',         0, 'menu', '/user',                   2),
(3, 'user:list',            '用户列表（查改删）',     2, 'api',  '/api/admin/user/**',         1),
(4, 'user:role',            '角色管理（增删改）',     2, 'api',  '/api/admin/role/**',         2),
(13,'user:perm',            '权限分配',            2, 'api',  '/api/admin/permission/**',    3),

-- ====== 内部文档管理 ======
(5, 'doc:manage',           '内部文档管理',         0, 'menu', '/doc',                    3),
(6, 'doc:crud',             '文档增删改查',         5, 'api',  '/api/doc/**',               1),

-- ====== 文献管理 ======
(7, 'literature:manage',    '文献管理',            0, 'menu', '/literature',              4),
(8, 'literature:upload',    '上传文献（单条）',        7, 'api',  '/api/document/upload',      1),
(25,'literature:batchUpload','批量上传文献（管理员）',    7, 'api',  '/api/document/batch-upload',  1),
(9, 'literature:download',  '下载文献',            7, 'api',  '/api/document/*/download',    2),
(10,'literature:folder',    '目录管理',            7, 'api',  '/api/rag/folder/**',         3),
(11,'literature:recycle',   '回收站/恢复/彻底删除',    7, 'api',  '/api/document/recycle-bin',   4),
(12,'literature:view',      '文献查看/搜索/详情',      7, 'api',  '/api/document/folder',       5),

-- ====== 全局搜索 ======
(16,'search:manage',        '全局搜索',            0, 'menu', '/search',                  5),
(17,'search:api',           '搜索接口',            16,'api',  '/api/search/**',            1),

-- ====== AI问答 ======
(18,'rag:chat',             'AI问答',             0, 'menu', '/rag',                     6),
(19,'rag:session',          '会话管理',            18,'api',  '/api/session/**',           1),
(20,'rag:stream',           '对话流',             18,'api',  '/api/chat/**',              2),
(21,'rag:search',           '向量搜索',            18,'api',  '/api/search/vector-search',  3),
(22,'rag:citation',         '引用下载',            18,'api',  '/api/citation/**',          4),

-- ====== 通知中心 ======
(23,'notification',         '通知中心',            0, 'menu', '/notification',            7),
(24,'notification:manage',  '通知管理',            23,'api',  '/api/notification/**',      1);

-- 3. 重新分配角色权限

-- admin：全部权限
INSERT INTO `role_permission` (`role_id`, `perm_id`) VALUES
(1,1),(1,2),(1,3),(1,4),(1,13),(1,5),(1,6),(1,7),(1,8),(1,25),(1,9),(1,10),(1,11),(1,12),(1,16),(1,17),(1,18),(1,19),(1,20),(1,21),(1,22),(1,23),(1,24);

-- teacher：首页、内部文档、文献（上传/下载/查看）、搜索、AI问答（不含ES验证）、通知
INSERT INTO `role_permission` (`role_id`, `perm_id`) VALUES
(2,1),(2,5),(2,6),(2,7),(2,8),(2,9),(2,10),(2,12),(2,16),(2,17),(2,18),(2,19),(2,20),(2,21),(2,22),(2,23),(2,24);

-- student：首页、内部文档、文献（上传/下载/查看，不含目录管理和回收站）、搜索、AI问答、通知
INSERT INTO `role_permission` (`role_id`, `perm_id`) VALUES
(3,1),(3,5),(3,6),(3,7),(3,8),(3,9),(3,12),(3,16),(3,17),(3,18),(3,19),(3,20),(3,21),(3,22),(3,23),(3,24);

-- guest：首页、文献查看、搜索
INSERT INTO `role_permission` (`role_id`, `perm_id`) VALUES
(4,1),(4,7),(4,12),(4,16),(4,17);
