package com.laboa.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.laboa.common.result.Result;
import com.laboa.system.entity.RolePermission;
import com.laboa.system.entity.SysPermission;
import com.laboa.system.entity.SysRole;
import com.laboa.system.mapper.RolePermissionMapper;
import com.laboa.system.mapper.SysPermissionMapper;
import com.laboa.system.mapper.SysRoleMapper;
import com.laboa.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckPermission("user:perm")
@RestController
@RequestMapping("/api/admin/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionMapper sysPermissionMapper;
    private final SysRoleMapper sysRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    /** 获取所有权限列表 */
    @GetMapping("/list")
    public Result<List<SysPermission>> listAll() {
        List<SysPermission> list = sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSort)
        );
        return Result.success(list);
    }

    /** 获取指定角色的权限ID列表 */
    @GetMapping("/role/{roleId}")
    public Result<List<Long>> getByRole(@PathVariable("roleId") Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        List<Long> permIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId)
        ).stream().map(RolePermission::getPermId).toList();
        return Result.success(permIds);
    }

    /** 设置角色的权限 */
    @PutMapping("/role/{roleId}")
    public Result<Void> setRolePermissions(@PathVariable("roleId") Long roleId,
                                           @RequestBody List<Long> permIds) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        // 清除旧权限
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId)
        );
        // 设置新权限
        if (permIds != null && !permIds.isEmpty()) {
            for (Long permId : permIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
        return Result.success();
    }
}
