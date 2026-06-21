package com.laboa.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置
 * 生产环境务必通过环境变量 CORS_ALLOWED_ORIGINS 指定前端域名，
 * 禁止 allowedOriginPatterns("*") + allowCredentials(true) 同时开启。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        var registration = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        if (origins.isEmpty()) {
            // 未配置允许来源时，仅开放简单跨域，不携带凭证，降低 CSRF 风险
            registration.allowedOrigins("*");
        } else {
            // 配置了具体来源时，才允许携带凭证
            registration.allowedOrigins(origins.toArray(new String[0]))
                    .allowCredentials(true);
        }
    }
}