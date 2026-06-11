SET NAMES utf8mb4;

ALTER DATABASE lab_oa CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE sys_user SET real_name = CONVERT(CAST(CONVERT(real_name USING latin1) AS BINARY) USING utf8mb4) WHERE username='admin';
UPDATE sys_user SET real_name = CONVERT(CAST(CONVERT(real_name USING latin1) AS BINARY) USING utf8mb4) WHERE username='teacher';
UPDATE sys_user SET real_name = CONVERT(CAST(CONVERT(real_name USING latin1) AS BINARY) USING utf8mb4) WHERE username='student';
UPDATE sys_user SET real_name = CONVERT(CAST(CONVERT(real_name USING latin1) AS BINARY) USING utf8mb4) WHERE username='guest';

UPDATE sys_role SET role_name = CONVERT(CAST(CONVERT(role_name USING latin1) AS BINARY) USING utf8mb4), description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4) WHERE role_code='admin';
UPDATE sys_role SET role_name = CONVERT(CAST(CONVERT(role_name USING latin1) AS BINARY) USING utf8mb4), description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4) WHERE role_code='teacher';
UPDATE sys_role SET role_name = CONVERT(CAST(CONVERT(role_name USING latin1) AS BINARY) USING utf8mb4), description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4) WHERE role_code='student';
UPDATE sys_role SET role_name = CONVERT(CAST(CONVERT(role_name USING latin1) AS BINARY) USING utf8mb4), description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4) WHERE role_code='guest';

UPDATE sys_permission SET perm_name = CONVERT(CAST(CONVERT(perm_name USING latin1) AS BINARY) USING utf8mb4);
