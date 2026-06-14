package com.laboa.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.system.dto.UserPageDTO;
import com.laboa.system.entity.SysUser;
import com.laboa.system.service.SysUserService;
import com.laboa.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SaCheckPermission("user:list")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService sysUserService;

    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(@ModelAttribute UserPageDTO dto) {
        PageResult<UserVO> pageResult = sysUserService.page(dto);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}/detail")
    public Result<UserVO> getDetail(@PathVariable("id") Long id) {
        UserVO vo = sysUserService.getById(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        sysUserService.updateStatus(id, status);
        return Result.success();
    }

    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable("id") Long id, @RequestBody List<Long> roleIds) {
        sysUserService.assignRoles(id, roleIds);
        return Result.success();
    }

    @PutMapping("/{id}/info")
    public Result<Void> updateInfo(@PathVariable("id") Long id, @RequestBody SysUser updateData) {
        sysUserService.updateUserInfo(id, updateData);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        sysUserService.deleteUser(id);
        return Result.success();
    }
}