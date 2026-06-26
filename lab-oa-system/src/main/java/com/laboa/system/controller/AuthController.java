package com.laboa.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.result.Result;
import com.laboa.security.service.SecurityService;
import com.laboa.system.dto.LoginDTO;
import com.laboa.system.dto.RegisterDTO;
import com.laboa.system.entity.LoginLog;
import com.laboa.system.mapper.LoginLogMapper;
import com.laboa.system.service.SecurityQuestionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final SecurityService securityService;
    private final SecurityQuestionService securityQuestionService;
    private final LoginLogMapper loginLogMapper;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        // IP级别注册限流
        String ip = securityService.getClientIp();
        String ipLimit = securityService.checkRegisterIpLimit(ip);
        if (ipLimit != null) {
            return Result.error(ipLimit);
        }

        // 密码强度校验
        String pwdError = securityService.validatePasswordStrength(dto.getPassword());
        if (pwdError != null) {
            return Result.error(pwdError);
        }

        sysUserService.register(dto);
        securityService.recordRegisterIp(ip);
        return Result.success();
    }

    /** 获取随机安全问题（2道，注册时使用） */
    @GetMapping("/security-questions")
    public Result<?> getSecurityQuestions() {
        var questions = securityQuestionService.getRandomQuestions(2);
        var result = questions.stream()
                .map(q -> Map.of("id", q.getId(), "question", q.getQuestion()))
                .toList();
        return Result.success(result);
    }

    /** 验证安全问题答案（异步验证，只返回正确/错误），含失败次数限制 */
    @PostMapping("/security-questions/verify")
    public Result<String> verifySecurityAnswers(@RequestBody Map<String, String> answers) {
        String ip = securityService.getClientIp();

        // 检查是否已被锁定
        String lockMsg = securityService.checkSecurityAnswerLocked(ip);
        if (lockMsg != null) {
            return Result.error(lockMsg);
        }

        boolean correct = securityQuestionService.verifyAnswers(answers);
        if (correct) {
            securityService.clearSecurityAnswerFailure(ip);
            return Result.success("验证通过");
        }

        // 记录失败
        String failMsg = securityService.recordSecurityAnswerFailure(ip);
        if (failMsg != null) {
            return Result.error(failMsg);
        }
        return Result.error("存在回答错误");
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        String username = dto.getUsername();
        String ip = securityService.getClientIp();
        String device = securityService.getDeviceInfo();

        // 1. 检查账号是否被临时锁定
        String lockMsg = securityService.checkAccountLocked(username);
        if (lockMsg != null) {
            return Result.error(lockMsg);
        }

        // 2. 检查是否需要验证码
        if (securityService.needCaptcha(username)) {
            if (dto.getCaptchaId() == null || dto.getCaptchaAnswer() == null) {
                return Result.error("请输入验证码");
            }
            if (!securityService.verifyCaptcha(dto.getCaptchaId(), dto.getCaptchaAnswer())) {
                return Result.error("验证码错误");
            }
        }

        // 3. 执行登录
        LoginVO loginVO;
        try {
            loginVO = sysUserService.login(dto);
        } catch (Exception e) {
            // 登录失败：记录失败次数 + 日志
            int failCount = securityService.recordLoginFailure(username);
            logLogin(username, null, ip, device, "LOGIN_FAIL", e.getMessage());
            // 返回错误信息，同时告知是否需要验证码
            String msg = e.getMessage();
            if (securityService.needCaptcha(username)) {
                msg += "（需要验证码）";
            }
            return Result.error(msg);
        }

        // 4. 登录成功：清除失败计数
        securityService.clearLoginFailure(username);

        // 5. 多端登录IP限制：查询最近24小时内活跃登录的不同IP数
        //    同一IP重复登录不占用配额，IP数达到上限（2个）时拒绝新IP登录
        Long userId = loginVO.getUser().getId();
        if (isNewIpLogin(userId, ip)) {
            long distinctIpCount = countDistinctLoginIps(userId);
            if (distinctIpCount >= 2) {
                return Result.error("登录IP数已达上限（最多2个不同IP），请在其他设备下线后再试");
            }
        }

        // 6. 同端互斥登录：踢掉同设备之前的会话
        String deviceType = dto.getDeviceType() != null ? dto.getDeviceType() : "web";
        StpUtil.login(loginVO.getUser().getId(), deviceType);
        loginVO.setToken(StpUtil.getTokenValue());

        // 7. 记录登录日志
        logLogin(username, loginVO.getUser().getId(), ip, device, "LOGIN", null);

        return Result.success(loginVO);
    }

    /** 获取验证码 */
    @GetMapping("/captcha")
    public Result<Map<String, Object>> getCaptcha(@RequestParam("username") String username) {
        boolean need = securityService.needCaptcha(username);
        if (!need) {
            return Result.success(Map.of("needCaptcha", false));
        }
        SecurityService.CaptchaResult captcha = securityService.generateCaptcha();
        return Result.success(Map.of(
                "needCaptcha", true,
                "captchaId", captcha.getCaptchaId(),
                "targetX", captcha.getTargetX()
        ));
    }

    /** 检查是否需要验证码 */
    @GetMapping("/captcha/check")
    public Result<Map<String, Object>> checkCaptcha(@RequestParam("username") String username) {
        boolean need = securityService.needCaptcha(username);
        if (!need) {
            return Result.success(Map.of("needCaptcha", false));
        }
        SecurityService.CaptchaResult captcha = securityService.generateCaptcha();
        return Result.success(Map.of(
                "needCaptcha", true,
                "captchaId", captcha.getCaptchaId(),
                "targetX", captcha.getTargetX()
        ));
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

    /** 验证当前用户密码（用于敏感操作的二次验证） */
    @PostMapping("/verify-password")
    public Result<Void> verifyPassword(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isBlank()) {
            return Result.error("密码不能为空");
        }
        boolean valid = sysUserService.verifyPassword(StpUtil.getLoginIdAsLong(), password);
        if (!valid) {
            return Result.error("密码错误");
        }
        return Result.success();
    }

    /** 退出登录：记录LOGOUT日志后注销会话，释放IP登录名额 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserVO userVO = sysUserService.getById(userId);
        String ip = securityService.getClientIp();
        String device = securityService.getDeviceInfo();
        // 记录登出日志
        logLogin(userVO.getUsername(), userId, ip, device, "LOGOUT", null);
        StpUtil.logout();
        return Result.success();
    }

    private void logLogin(String username, Long userId, String ip, String device, String action, String remark) {
        try {
            LoginLog log = new LoginLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setIp(ip);
            log.setDevice(device);
            log.setAction(action);
            log.setRemark(remark);
            log.setCreateTime(java.time.LocalDateTime.now());
            loginLogMapper.insert(log);
        } catch (Exception e) {
            // 日志写入失败不影响登录流程
            org.slf4j.LoggerFactory.getLogger(AuthController.class).warn("写入登录日志失败: {}", e.getMessage());
        }
    }

    /** 判断当前IP是否为新登录IP（最近24小时内未以此IP登录过） */
    private boolean isNewIpLogin(Long userId, String ip) {
        return loginLogMapper.selectCount(
                new LambdaQueryWrapper<LoginLog>()
                        .eq(LoginLog::getUserId, userId)
                        .eq(LoginLog::getIp, ip)
                        .eq(LoginLog::getAction, "LOGIN")
                        .ge(LoginLog::getCreateTime, java.time.LocalDateTime.now().minusHours(24))
        ) == 0;
    }

    /** 统计最近24小时内活跃登录的不同IP数（已登出的IP不计入） */
    private long countDistinctLoginIps(Long userId) {
        return loginLogMapper.countActiveDistinctIps(userId, java.time.LocalDateTime.now().minusHours(24));
    }
}
