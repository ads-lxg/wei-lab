package com.laboa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@Slf4j
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
@SpringBootApplication
public class LabOaApplication implements AsyncConfigurer {

    public static void main(String[] args) {
        SpringApplication.run(LabOaApplication.class, args);
    }

    /**
     * 捕获 @Async 方法中未处理的异常（默认会被静默吞掉）
     */
    @Override
    public org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("@Async 异常被吞掉! method={}, params={}, error={}",
                    method.getName(), Arrays.toString(params), ex.getMessage(), ex);
        };
    }
}