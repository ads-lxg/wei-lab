-- 修复因编辑问题导致 status 被误设为 NULL 的安全问题
-- 将所有 status=NULL 的题目恢复为启用状态（status=1）
UPDATE `security_question` SET `status` = 1 WHERE `status` IS NULL AND `deleted` = 0;
