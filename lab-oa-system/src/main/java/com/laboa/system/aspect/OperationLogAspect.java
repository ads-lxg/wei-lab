package com.laboa.system.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.system.entity.SysLog;
import com.laboa.system.entity.SysUser;
import com.laboa.system.mapper.SysLogMapper;
import com.laboa.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 操作日志 AOP 切面
 * 自动记录增删改操作的日志到 sys_log 表
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysLogMapper sysLogMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 拦截所有 POST / PUT / DELETE 请求（查询操作不记录）
     */
    @Around("(@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)) && " +
            "execution(* com.laboa..controller..*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        SysLog sysLog = new SysLog();
        sysLog.setRequestMethod(detectHttpMethod(joinPoint));
        sysLog.setRequestUrl(extractRequestUrl(joinPoint));
        sysLog.setModule(extractModule(joinPoint));
        sysLog.setAction(extractAction(joinPoint));
        sysLog.setTarget(extractTarget(joinPoint));
        sysLog.setIp(extractClientIp());

        // 获取当前用户信息
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId != null) {
                Long userId = Long.valueOf(loginId.toString());
                sysLog.setUserId(userId);
                SysUser user = sysUserMapper.selectById(userId);
                if (user != null) {
                    sysLog.setUsername(user.getUsername());
                    sysLog.setRealName(user.getRealName());
                }
            }
        } catch (Exception e) {
            // 未登录用户操作不记录 userId
        }

        sysLog.setCreateTime(LocalDateTime.now());

        try {
            Object result = joinPoint.proceed();
            sysLog.setResult("SUCCESS");
            sysLog.setCostTime(System.currentTimeMillis() - startTime);
            sysLogMapper.insert(sysLog);
            return result;
        } catch (Throwable e) {
            sysLog.setResult("FAIL");
            sysLog.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : e.getClass().getSimpleName());
            sysLog.setCostTime(System.currentTimeMillis() - startTime);
            sysLogMapper.insert(sysLog);
            throw e;
        }
    }

    private String detectHttpMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)) return "DELETE";
        return "UNKNOWN";
    }

    private String extractRequestUrl(ProceedingJoinPoint joinPoint) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getRequestURI();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractModule(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        if (className.contains("Document") || className.contains("Literature")) return "文献管理";
        if (className.contains("User")) return "用户管理";
        if (className.contains("Role")) return "角色管理";
        if (className.contains("Permission")) return "权限管理";
        if (className.contains("Config")) return "系统配置";
        if (className.contains("Folder")) return "目录管理";
        if (className.contains("File") || className.contains("Chunk")) return "文件管理";
        if (className.contains("Rag") || className.contains("Chat") || className.contains("Knowledge")) return "知识库";
        if (className.contains("Auth") || className.contains("Login")) return "认证登录";
        if (className.contains("Notification")) return "通知管理";
        if (className.contains("MdDocument")) return "技术文档";
        if (className.contains("Log")) return "系统管理";
        return className;
    }

    private String extractAction(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String methodName = method.getName();
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)) {
            if (methodName.contains("upload") || methodName.contains("create") || methodName.contains("add")) return "新增";
            if (methodName.contains("login")) return "登录";
            if (methodName.contains("register")) return "注册";
            if (methodName.contains("recover") || methodName.contains("restore")) return "恢复";
            if (methodName.contains("batch")) return "批量新增";
            return "新增";
        }
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)) {
            if (methodName.contains("update") || methodName.contains("modify") || methodName.contains("edit")) return "修改";
            if (methodName.contains("status") || methodName.contains("enable") || methodName.contains("disable")) return "状态变更";
            if (methodName.contains("move")) return "移动";
            if (methodName.contains("assign") || methodName.contains("grant")) return "授权";
            if (methodName.contains("batch")) return "批量修改";
            return "修改";
        }
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)) {
            if (methodName.contains("permanent")) return "彻底删除";
            if (methodName.contains("batch")) return "批量删除";
            return "删除";
        }
        return methodName;
    }

    private String extractTarget(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        // 优先从方法参数中提取具体的目标信息
        String specificTarget = extractTargetFromArgs(parameters, args);
        if (specificTarget != null) {
            return specificTarget;
        }

        // 降级：使用 @Operation summary 注解
        io.swagger.v3.oas.annotations.Operation operation = method.getAnnotation(io.swagger.v3.oas.annotations.Operation.class);
        if (operation != null && !operation.summary().isBlank()) {
            return operation.summary();
        }

        return method.getName();
    }

    /**
     * 从方法参数中智能提取操作目标信息
     * 支持：RequestParam 的 title/name/folderName/fileName/username/email/roleName/permName，
     *       MultipartFile 的原始文件名，
     *       RequestBody DTO 中的 title/name/username/email
     */
    private String extractTargetFromArgs(Parameter[] parameters, Object[] args) {
        if (parameters == null || args == null) return null;

        // 收集所有可能有意义的参数值
        String nameFromParam = null;
        String fileFromParam = null;

        for (int i = 0; i < parameters.length && i < args.length; i++) {
            Parameter param = parameters[i];
            Object arg = args[i];
            if (arg == null) continue;

            // 处理 MultipartFile → 记录原始文件名
            if (arg instanceof org.springframework.web.multipart.MultipartFile) {
                String fn = ((org.springframework.web.multipart.MultipartFile) arg).getOriginalFilename();
                if (fn != null && !fn.isBlank()) fileFromParam = fn;
                continue;
            }

            // 处理 @RequestParam 注解的参数
            org.springframework.web.bind.annotation.RequestParam rp = param.getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
            if (rp != null) {
                String val = arg.toString();
                if (val.isBlank() || val.equals("0") || val.equals("1")) continue;
                String name = rp.name().isBlank() ? rp.value() : rp.name();
                if (name.equals("title") || name.equals("name") || name.equals("folderName") ||
                    name.equals("fileName") || name.equals("realName") || name.equals("username") ||
                    name.equals("roleName") || name.equals("permName") || name.equals("email") ||
                    name.equals("password")) {
                    nameFromParam = val;
                }
                continue;
            }

            // 处理 @RequestBody DTO → 反射提取 title/name/username/email 等字段
            org.springframework.web.bind.annotation.RequestBody rb = param.getAnnotation(org.springframework.web.bind.annotation.RequestBody.class);
            if (rb != null) {
                try {
                    // 优先提取有意义字段
                    String[] fieldsToCheck = {"title", "name", "username", "realName", "email", "folderName", "roleName", "permName", "fileName"};
                    for (String field : fieldsToCheck) {
                        try {
                            java.beans.PropertyDescriptor pd = new java.beans.PropertyDescriptor(field, arg.getClass());
                            Object val = pd.getReadMethod().invoke(arg);
                            if (val != null && !val.toString().isBlank()) {
                                nameFromParam = val.toString();
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
                continue;
            }

            // 处理 @PathVariable → 记录值（如用户ID、文献ID等），但不单独使用
            // 只有文件名优先时用作补充
        }

        // 组装目标描述
        StringBuilder sb = new StringBuilder();
        if (fileFromParam != null) {
            sb.append(fileFromParam);
        }
        if (nameFromParam != null) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(nameFromParam);
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    private String extractClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                // X-Forwarded-For 可能有多个 IP，取第一个
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}
