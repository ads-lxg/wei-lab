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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        loginVO.setToken(StpUtil.getTokenInfo().getTokenValue());
        return Result.success(loginVO);
    }

    @GetMapping("/info")
    public Result<UserVO> info() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserVO userVO = sysUserService.getById(userId);
        return Result.success(userVO);
    }
}