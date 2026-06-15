package com.laboa.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.system.dto.SysLogSearchDTO;
import com.laboa.system.service.SysLogService;
import com.laboa.system.vo.SysLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 操作日志管理
 */
@Tag(name = "操作日志", description = "操作日志的查询、详情、删除（仅ADMIN可操作）")
@RestController
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class SysLogController {

    private final SysLogService sysLogService;

    @Operation(summary = "分页搜索操作日志")
    @GetMapping("/page")
    public Result<PageResult<SysLogVO>> page(SysLogSearchDTO dto) {
        return Result.success(sysLogService.search(dto));
    }

    @Operation(summary = "查询日志详情")
    @GetMapping("/{id}")
    public Result<SysLogVO> getDetail(@PathVariable("id") Long id) {
        return Result.success(sysLogService.getById(id));
    }

    @Operation(summary = "删除操作日志")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        sysLogService.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "批量删除操作日志")
    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        sysLogService.batchDelete(ids);
        return Result.success();
    }
}
