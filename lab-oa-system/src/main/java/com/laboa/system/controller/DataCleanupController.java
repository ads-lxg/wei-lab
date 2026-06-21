package com.laboa.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.laboa.common.result.Result;
import com.laboa.system.scheduler.DataCleanupJob;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据清理管理接口（管理员手动清理已删除数据）
 */
@SaCheckRole("admin")
@RestController
@RequestMapping("/api/admin/data-cleanup")
@RequiredArgsConstructor
public class DataCleanupController {

    private final DataCleanupJob dataCleanupJob;

    /** 手动执行清理 */
    @PostMapping("/execute")
    public Result<Map<String, Integer>> executeCleanup() {
        Map<String, Integer> result = dataCleanupJob.cleanup();
        return Result.success(result);
    }
}
