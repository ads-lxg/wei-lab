package com.laboa.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.result.Result;
import com.laboa.system.dto.LoginDTO;
import com.laboa.system.dto.RegisterDTO;
import com.laboa.system.service.SysUserService;
import com.laboa.system.vo.LoginVO;
import com.laboa.system.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        sysUserService.register(dto);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO loginVO = sysUserService.login(dto);
        StpUtil.login(loginVO.getUser().getId());
        loginVO.setToken(StpUtil.getTokenValue());
        return Result.success(loginVO);
    }

    @GetMapping("/info")
    public Result<UserVO> info() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserVO userVO = sysUserService.getById(userId);
        return Result.success(userVO);
    }

    /** 上传/更换头像（当前登录用户） */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestPart("file") MultipartFile file) {
        Long userId = StpUtil.getLoginIdAsLong();
        String url = sysUserService.uploadAvatar(userId, file);
        return Result.success(url);
    }

    /** 删除头像（恢复默认） */
    @DeleteMapping("/avatar")
    public Result<Void> deleteAvatar() {
        Long userId = StpUtil.getLoginIdAsLong();
        sysUserService.deleteAvatar(userId);
        return Result.success();
    }
}