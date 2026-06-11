package com.laboa.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 配置类
 * 外部服务客户端（WebClient）由各 ServiceImpl 自行初始化（@PostConstruct）
 * 此类保留用于未来扩展
 */
@Slf4j
@Configuration
public class RagConfig {
}
