package com.laboa.system.controller;

import com.laboa.common.result.Result;
import com.laboa.system.entity.SysRole;
import com.laboa.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @PutMapping
    public Result<Void> update(@RequestBody SysRole role) {
        sysRoleService.update(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        sysRoleService.delete(id);
        return Result.success();
    }
}