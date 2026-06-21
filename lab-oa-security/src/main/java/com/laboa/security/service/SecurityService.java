package com.laboa.security.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 安全服务：登录失败计数、账号临时锁定、IP限流、验证码校验
 * 基于内存实现，适用于单实例部署
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityService {

    // ===== 登录失败计数 + 账号临时锁定 =====
    private static final int MAX_LOGIN_FAILURES = 5;          // 最大失败次数
    private static final long LOCK_DURATION_MINUTES = 30;     // 锁定时长(分钟)

    @Data
    private static class LoginFailureRecord {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile LocalDateTime lockUntil;
    }

    private final Map<String, LoginFailureRecord> loginFailureMap = new ConcurrentHashMap<>();

    /** 记录登录失败，返回当前失败次数 */
    public int recordLoginFailure(String username) {
        LoginFailureRecord record = loginFailureMap.computeIfAbsent(username, k -> new LoginFailureRecord());
        int count = record.getCount().incrementAndGet();
        if (count >= MAX_LOGIN_FAILURES) {
            record.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("账号 {} 登录失败 {} 次，已锁定至 {}", username, count, record.getLockUntil());
        }
        return count;
    }

    /** 清除登录失败记录（登录成功时调用） */
    public void clearLoginFailure(String username) {
        loginFailureMap.remove(username);
    }

    /** 检查账号是否被锁定，返回错误信息或null */
    public String checkAccountLocked(String username) {
        LoginFailureRecord record = loginFailureMap.get(username);
        if (record == null || record.getLockUntil() == null) return null;
        if (LocalDateTime.now().isBefore(record.getLockUntil())) {
            long remainMinutes = java.time.Duration.between(LocalDateTime.now(), record.getLockUntil()).toMinutes() + 1;
            return "账号已被临时锁定，请 " + remainMinutes + " 分钟后再试";
        }
        // 锁定已过期，清除记录
        loginFailureMap.remove(username);
        return null;
    }

    /** 获取账号登录失败次数 */
    public int getLoginFailureCount(String username) {
        LoginFailureRecord record = loginFailureMap.get(username);
        return record != null ? record.getCount().get() : 0;
    }

    /** 是否需要验证码（失败3次后需要） */
    public boolean needCaptcha(String username) {
        return getLoginFailureCount(username) >= 3;
    }

    // ===== IP级别注册限流 =====
    private static final int MAX_REGISTER_PER_IP_PER_HOUR = 5;

    @Data
    private static class IpRegisterRecord {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile LocalDateTime resetTime;
    }

    private final Map<String, IpRegisterRecord> ipRegisterMap = new ConcurrentHashMap<>();

    /** 检查IP注册频率，返回错误信息或null */
    public String checkRegisterIpLimit(String ip) {
        IpRegisterRecord record = ipRegisterMap.computeIfAbsent(ip, k -> {
            IpRegisterRecord r = new IpRegisterRecord();
            r.setResetTime(LocalDateTime.now().plusHours(1));
            return r;
        });

        // 检查是否需要重置计数
        if (LocalDateTime.now().isAfter(record.getResetTime())) {
            record.getCount().set(0);
            record.setResetTime(LocalDateTime.now().plusHours(1));
        }

        if (record.getCount().get() >= MAX_REGISTER_PER_IP_PER_HOUR) {
            return "该IP注册过于频繁，请1小时后再试";
        }
        return null;
    }

    /** 记录IP注册次数 */
    public void recordRegisterIp(String ip) {
        IpRegisterRecord record = ipRegisterMap.computeIfAbsent(ip, k -> {
            IpRegisterRecord r = new IpRegisterRecord();
            r.setResetTime(LocalDateTime.now().plusHours(1));
            return r;
        });
        record.getCount().incrementAndGet();
    }

    // ===== 工具方法 =====

    /** 获取当前请求的客户端IP */
    public String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /** 获取当前请求的设备信息 */
    public String getDeviceInfo() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest request = attrs.getRequest();
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "unknown";
        // 简化UA信息
        if (ua.contains("Chrome")) return "Chrome " + extractVersion(ua, "Chrome");
        if (ua.contains("Firefox")) return "Firefox " + extractVersion(ua, "Firefox");
        if (ua.contains("Safari") && !ua.contains("Chrome")) return "Safari " + extractVersion(ua, "Safari");
        if (ua.contains("Edg")) return "Edge " + extractVersion(ua, "Edg");
        return ua.length() > 100 ? ua.substring(0, 100) : ua;
    }

    private String extractVersion(String ua, String browser) {
        int idx = ua.indexOf(browser);
        if (idx < 0) return "";
        String sub = ua.substring(idx + browser.length() + 1);
        int dotIdx = sub.indexOf(' ');
        String version = dotIdx > 0 ? sub.substring(0, Math.min(dotIdx, 10)) : sub.substring(0, Math.min(sub.length(), 10));
        return version;
    }

    // ===== 滑动验证码 =====
    private final Map<String, CaptchaRecord> captchaMap = new ConcurrentHashMap<>();

    @Data
    private static class CaptchaRecord {
        private int targetX;        // 目标X位置（百分比 0-100）
        private LocalDateTime expireTime;
    }

    /** 生成滑动验证码，返回验证码ID和目标位置 */
    public CaptchaResult generateCaptcha() {
        int targetX = (int) (Math.random() * 60) + 20; // 20-80之间，确保不会太靠边
        String captchaId = java.util.UUID.randomUUID().toString().replace("-", "");
        CaptchaRecord record = new CaptchaRecord();
        record.setTargetX(targetX);
        record.setExpireTime(LocalDateTime.now().plusMinutes(5));
        captchaMap.put(captchaId, record);
        return new CaptchaResult(captchaId, targetX);
    }

    /** 校验滑动验证码，允许±5%的误差 */
    public boolean verifyCaptcha(String captchaId, String userInput) {
        if (captchaId == null || userInput == null) return false;
        CaptchaRecord record = captchaMap.remove(captchaId);
        if (record == null) return false;
        if (LocalDateTime.now().isAfter(record.getExpireTime())) return false;
        try {
            int userX = Integer.parseInt(userInput.trim());
            return Math.abs(userX - record.getTargetX()) <= 5;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Data
    public static class CaptchaResult {
        private final String captchaId;
        private final int targetX;
    }

    // ===== 安全问题验证失败计数 + 锁定 =====
    /** 最大安全答案验证失败次数 */
    private static final int MAX_SECURITY_ANSWER_FAILURES = 3;
    /** 安全答案锁定时间（分钟） */
    private static final long SECURITY_LOCK_DURATION_MINUTES = 5;

    @Data
    private static class SecurityAnswerRecord {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile LocalDateTime lockUntil;
    }

    private final Map<String, SecurityAnswerRecord> securityAnswerMap = new ConcurrentHashMap<>();

    /**
     * 记录安全问题答案验证失败，达到上限后锁定
     * @param ip 客户端IP
     * @return 锁定错误信息，null表示未锁定
     */
    public String recordSecurityAnswerFailure(String ip) {
        SecurityAnswerRecord record = securityAnswerMap.computeIfAbsent(ip, k -> new SecurityAnswerRecord());
        int count = record.count.incrementAndGet();
        if (count >= MAX_SECURITY_ANSWER_FAILURES) {
            record.setLockUntil(LocalDateTime.now().plusMinutes(SECURITY_LOCK_DURATION_MINUTES));
            return "安全问题回答错误次数过多，注册通道已锁定" + SECURITY_LOCK_DURATION_MINUTES + "分钟";
        }
        return null;
    }

    /**
     * 检查IP是否被安全问题锁定
     * @return 错误信息，null表示通过
     */
    public String checkSecurityAnswerLocked(String ip) {
        SecurityAnswerRecord record = securityAnswerMap.get(ip);
        if (record == null || record.getLockUntil() == null) return null;
        if (LocalDateTime.now().isBefore(record.getLockUntil())) {
            long remainMinutes = java.time.Duration.between(LocalDateTime.now(), record.getLockUntil()).toMinutes() + 1;
            return "安全问题回答错误次数过多，注册通道已锁定，请 " + remainMinutes + " 分钟后再试";
        }
        // 锁定已过期，清除记录
        securityAnswerMap.remove(ip);
        return null;
    }

    /** 清除安全问题失败记录（验证通过时调用） */
    public void clearSecurityAnswerFailure(String ip) {
        securityAnswerMap.remove(ip);
    }

    // ===== 密码强度校验 =====

    /**
     * 校验密码强度：至少8位，包含大小写字母和数字
     * @return 错误信息，null表示通过
     */
    public String validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return "密码长度不能少于8位";
        }
        if (!password.matches(".*[a-z].*")) {
            return "密码必须包含小写字母";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "密码必须包含大写字母";
        }
        if (!password.matches(".*\\d.*")) {
            return "密码必须包含数字";
        }
        return null;
    }
}
