SET NAMES utf8mb4;

UPDATE sys_user SET real_name='系统管理员' WHERE username='admin';
UPDATE sys_user SET real_name='张教授' WHERE username='teacher';
UPDATE sys_user SET real_name='李同学' WHERE username='student';
UPDATE sys_user SET real_name='游客' WHERE username='guest';

UPDATE sys_role SET role_name='管理员', description='系统管理员，拥有所有权限' WHERE role_code='admin';
UPDATE sys_role SET role_name='教师', description='教师角色，可上传下载文献，可访问内部文档' WHERE role_code='teacher';
UPDATE sys_role SET role_name='学生', description='学生角色，可上传下载文献，可访问内部文档' WHERE role_code='student';
UPDATE sys_role SET role_name='游客', description='游客角色，仅可在线查看文献' WHERE role_code='guest';

UPDATE sys_permission SET perm_name='仪表盘' WHERE perm_code='dashboard';
UPDATE sys_permission SET perm_name='用户管理' WHERE perm_code='user:manage';
UPDATE sys_permission SET perm_name='用户列表' WHERE perm_code='user:list';
UPDATE sys_permission SET perm_name='角色分配' WHERE perm_code='user:role';
UPDATE sys_permission SET perm_name='文档管理' WHERE perm_code='doc:manage';
UPDATE sys_permission SET perm_name='文档CRUD' WHERE perm_code='doc:crud';
UPDATE sys_permission SET perm_name='文献管理' WHERE perm_code='literature:manage';
UPDATE sys_permission SET perm_name='文献CRUD' WHERE perm_code='literature:crud';
UPDATE sys_permission SET perm_name='文献下载' WHERE perm_code='literature:download';
UPDATE sys_permission SET perm_name='全局搜索' WHERE perm_code='search';
UPDATE sys_permission SET perm_name='AI问答' WHERE perm_code='rag:chat';
UPDATE sys_permission SET perm_name='通知中心' WHERE perm_code='notification';
