package com.laboa.rag.config;

import com.laboa.rag.service.ResourceTextService;
import com.laboa.rag.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ES 索引初始化器
 * 应用启动后自动创建 doc_chunks（向量索引）和 resource_text（全文索引）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsIndexInitializer {

    private final VectorStoreService vectorStoreService;
    private final ResourceTextService resourceTextService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            vectorStoreService.createIndexIfNotExists();
            log.info("ES doc_chunks 索引初始化完成");
        } catch (Exception e) {
            log.warn("ES doc_chunks索引初始化失败（ES可能未启动）: {}", e.getMessage());
        }

        try {
            resourceTextService.createIndexIfNotExists();
            log.info("ES resource_text 索引初始化完成");
        } catch (Exception e) {
            log.warn("ES resource_text索引初始化失败（ES可能未启动）: {}", e.getMessage());
        }
    }
}
