package com.laboa.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.laboa.common.result.Result;
import com.laboa.system.entity.SysRole;
import com.laboa.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@SaCheckPermission("user:role")
@RestController
@RequestMapping("/api/admin/role")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleService sysRoleService;

    @GetMapping("/list")
    public Result<List<SysRole>> listAll() {
        List<SysRole> roles = sysRoleService.listAll();
        return Result.success(roles);
    }

    @PostMapping
    public Result<Void> save(@RequestBody SysRole role) {
        sysRoleService.save(role);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody SysRole role) {
        role.setId(id);
        sysRoleService.update(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        sysRoleService.delete(id);
        return Result.success();
    }
}