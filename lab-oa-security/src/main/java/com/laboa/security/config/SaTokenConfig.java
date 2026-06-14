package com.laboa.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.listener.SaTokenListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，使 @SaCheckRole / @SaCheckPermission 等注解生效
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/register",
                        "/ws/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/doc.html"
                );
    }

    @Bean
    public SaTokenListener getSaTokenListener() {
        return new SaTokenListener() {
            @Override
            public void doLogin(String loginType, Object loginId, String tokenValue, cn.dev33.satoken.stp.SaLoginModel loginModel) {
                log.debug("用户登录: loginType={}, loginId={}", loginType, loginId);
            }

            @Override
            public void doLogout(String loginType, Object loginId, String tokenValue) {
                log.debug("用户注销: loginType={}, loginId={}", loginType, loginId);
            }

            @Override
            public void doKickout(String loginType, Object loginId, String tokenValue) {
                log.debug("用户被踢下线: loginType={}, loginId={}", loginType, loginId);
            }

            @Override
            public void doReplaced(String loginType, Object loginId, String tokenValue) {
                log.debug("用户被顶下线: loginType={}, loginId={}", loginType, loginId);
            }

            @Override
            public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
                log.debug("账号被封禁: loginType={}, loginId={}, service={}", loginType, loginId, service);
            }

            @Override
            public void doUntieDisable(String loginType, Object loginId, String service) {
                log.debug("账号解封: loginType={}, loginId={}, service={}", loginType, loginId, service);
            }

            @Override
            public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {
                log.debug("打开二级认证: loginType={}, service={}", loginType, service);
            }

            @Override
            public void doCloseSafe(String loginType, String tokenValue, String service) {
                log.debug("关闭二级认证: loginType={}, service={}", loginType, service);
            }

            @Override
            public void doCreateSession(String id) {
                log.debug("创建会话: id={}", id);
            }

            @Override
            public void doLogoutSession(String id) {
                log.debug("注销会话: id={}", id);
            }

            @Override
            public void doRenewTimeout(String tokenValue, Object loginId, long timeout) {
                log.debug("续签超时: loginId={}", loginId);
            }
        };
    }

}