package com.laboa.system.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 定时清理软删除数据（deleted=1 且 deleted_time 超过90天）
 * 每天凌晨3点执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupJob {

    private final JdbcTemplate jdbcTemplate;

    /** 保留天数 */
    private static final int RETENTION_DAYS = 10;

    /** 所有使用逻辑删除的表 */
    private static final List<String> SOFT_DELETE_TABLES = List.of(
            "sys_user",
            "sys_role",
            "sys_permission",
            "security_question",
            "literature",
            "rag_folder",
            "md_document",
            "chat_session",
            "notification",
            "minio_file"
    );

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredDeletedData() {
        log.info("开始定时清理已删除数据（保留{}天）", RETENTION_DAYS);
        Map<String, Integer> result = cleanup();
        log.info("定时清理完成: {}", result);
    }

    /**
     * 执行清理，返回各表删除行数
     */
    public Map<String, Integer> cleanup() {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String table : SOFT_DELETE_TABLES) {
            try {
                String sql = "DELETE FROM `" + table + "` WHERE deleted = 1 AND deleted_time IS NOT NULL AND deleted_time < DATE_SUB(NOW(), INTERVAL ? DAY)";
                int count = jdbcTemplate.update(sql, RETENTION_DAYS);
                if (count > 0) {
                    counts.put(table, count);
                    log.info("清理表 {}: {} 条", table, count);
                }
            } catch (Exception e) {
                log.warn("清理表 {} 失败: {}", table, e.getMessage());
            }
        }
        return counts;
    }
}
