package com.laboa.system.controller;

import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.system.dto.UserPageDTO;
import com.laboa.system.service.SysUserService;
import com.laboa.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}