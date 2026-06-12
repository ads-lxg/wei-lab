-- =============================================
-- 文献表字段扩展迁移
-- 功能：
--   1. publish_year INT → publish_date DATE
--   2. 新增 source_journal 来源期刊字段
-- 执行前请备份数据！
-- =============================================

-- 1. 新增来源期刊字段
ALTER TABLE `literature`
    ADD COLUMN `source_journal` VARCHAR(300) NULL COMMENT '来源期刊' AFTER `doi`;

-- 2. 新增 publish_date 列（先加后迁移数据，再删旧列）
ALTER TABLE `literature`
    ADD COLUMN `publish_date` DATE NULL COMMENT '发表日期' AFTER `keywords`;

-- 3. 将旧 publish_year 数据迁移到 publish_date（年份转日期，默认取当年1月1日）
UPDATE `literature`
SET `publish_date` = STR_TO_DATE(CONCAT(`publish_year`, '-01-01'), '%Y-%m-%d')
WHERE `publish_year` IS NOT NULL;

-- 4. 删除旧列
ALTER TABLE `literature` DROP COLUMN `publish_year`;

-- 5. 添加新索引（idx_publish_year 已在步骤4删除列时自动级联删除，无需手动删除）
ALTER TABLE `literature`
    ADD INDEX `idx_publish_date` (`publish_date`);
