UPDATE sys_user SET password='$2a$10$mScmBVs31E0r7KXrIWXeROJBAcDVo4RJ1/La6a7hUs9yvGCrTjF6m' WHERE username IN ('admin','teacher','student','guest');
DELETE FROM sys_user WHERE username='hashtest';
