package com.laboa.rag.listener;

import com.laboa.common.constant.MqConstants;
import com.laboa.common.event.DocumentCreatedEvent;
import com.laboa.doc.mapper.MdDocumentMapper;
import com.laboa.file.service.FileService;
import com.laboa.literature.mapper.LiteratureMapper;
import com.laboa.rag.service.DocumentIngestionService;
import com.laboa.rag.service.ResourceTextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 文档事件监听器
 * 监听文献/文档的创建事件，自动执行：
 * 1. 从 byte[] 直接解析正文（按文件类型用专用解析器）
 * 2. 存入 ES resource_text（全文检索）→ 更新 parse_status=SUCCESS
 * 3. 分块 + 向量化 + 存入 ES doc_chunks（向量检索）—— 独立异步，不阻塞主流程
 *
 * 关键设计：步骤2完成后立即标记 SUCCESS，步骤3 作为 fire-and-forget 后处理，
 * 避免 Embedding API 不可用时阻塞整个解析流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentEventListener implements org.springframework.beans.factory.SmartInitializingSingleton {

    private final DocumentIngestionService documentIngestionService;
    private final ResourceTextService resourceTextService;
    private final FileService fileService;
    private final LiteratureMapper literatureMapper;
    private final MdDocumentMapper mdDocumentMapper;

    /** doc_chunks 向量入库专用线程池（独立，不阻塞主流程） */
    private final Executor indexingExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "es-index-worker");
                t.setDaemon(true);
                return t;
            }
    );

    /**
     * Bean 初始化完成后打印日志，确认 bean 已注册
     */
    @Override
    public void afterSingletonsInstantiated() {
        log.info("========== DocumentEventListener 已注册! 线程池已就绪 ==========");
    }

    /**
     * 文档创建事件：异步解析正文并入库
     * 使用 asyncTaskExecutor 线程池
     */
    @Async("asyncTaskExecutor")
    @EventListener
    public void onDocumentCreated(DocumentCreatedEvent event) {
        final boolean isRagSource = event.isRagSource();
        long totalStart = System.currentTimeMillis();
        log.info("========== 开始处理文档: docType={}, docId={}, fileName={}, ragSource={}, contentSize={} ==========",
                event.getDocType(), event.getDocId(), event.getFileName(),
                isRagSource, event.getFileContent() != null ? event.getFileContent().length : 0);

        try {
            // 并发保护：检查是否已被删除
            if (isDeleted(event.getDocType(), event.getDocId())) {
                log.warn("文档已被删除，放弃入库: docType={}, docId={}", event.getDocType(), event.getDocId());
                return;
            }

            // ===== 步骤1: 解析正文 =====
            long parseStart = System.currentTimeMillis();
            byte[] fileContent = event.getFileContent();
            if (fileContent == null || fileContent.length == 0) {
                log.warn("文件内容为空，放弃入库: docType={}, docId={}", event.getDocType(), event.getDocId());
                updateParseStatus(event.getDocType(), event.getDocId(), "FAILED");
                return;
            }

            String plainText = fileService.extractText(fileContent, event.getFileName());
            long parseTime = System.currentTimeMillis() - parseStart;
            log.info("[耗时] 步骤1-文本解析: {}ms, textLength={}", parseTime, plainText != null ? plainText.length() : 0);

            if (plainText == null || plainText.isBlank()) {
                log.warn("文档正文为空，放弃入库: docType={}, docId={}", event.getDocType(), event.getDocId());
                updateParseStatus(event.getDocType(), event.getDocId(), "FAILED");
                return;
            }

            // 再次检查是否已被删除
            if (isDeleted(event.getDocType(), event.getDocId())) {
                log.warn("文档解析后已被删除，放弃入库: docType={}, docId={}", event.getDocType(), event.getDocId());
                return;
            }

            String fileType = getFileType(event.getFileName());
            String title = getTitle(event.getDocType(), event.getDocId());

            // ===== 步骤2: 存入 ES resource_text（全文检索，ragSource=0/1 都执行）=====
            long textIndexStart = System.currentTimeMillis();
            resourceTextService.indexText(event.getDocId(), event.getDocType(), title, fileType, plainText);
            long textIndexTime = System.currentTimeMillis() - textIndexStart;
            log.info("[耗时] 步骤2-resource_text入库: {}ms", textIndexTime);

            // ===== 步骤3: 标记 parse_status=SUCCESS =====
            updateParseStatus(event.getDocType(), event.getDocId(), "SUCCESS");

            long mainFlowTime = System.currentTimeMillis() - totalStart;

            if (!isRagSource) {
                // ragSource=0: 仅全文检索，不做分块/向量化
                log.info("========== 全文检索入库完成(ragSource=0): docType={}, docId={}, 总耗时={}ms (解析={}ms, ES={}ms) ==========",
                        event.getDocType(), event.getDocId(), mainFlowTime, parseTime, textIndexTime);
                return;
            }

            // ===== ragSource=1: 继续分块 + 向量化 =====
            log.info("========== RAG主流程完成(ragSource=1): docType={}, docId={}, 总耗时={}ms (解析={}ms, ES={}ms) ==========",
                    event.getDocType(), event.getDocId(), mainFlowTime, parseTime, textIndexTime);

            // ===== 步骤4: 异步向量化入库（fire-and-forget，不阻塞主流程）=====
            String plainTextRef = plainText;
            CompletableFuture.runAsync(() -> {
                long chunkStart = System.currentTimeMillis();
                try {
                    documentIngestionService.ingestText(
                            plainTextRef, event.getFileName(),
                            event.getDocType() + "/" + event.getDocId(),
                            event.getDocType(), event.getDocId()
                    );
                    log.info("[耗时] 步骤4-doc_chunks向量入库完成: {}ms", System.currentTimeMillis() - chunkStart);
                } catch (Exception e) {
                    log.error("doc_chunks向量入库失败（不影响全文检索）: docType={}, docId={}, error={}",
                            event.getDocType(), event.getDocId(), e.getMessage(), e);
                }
            }, indexingExecutor);

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - totalStart;
            log.error("========== 文档入库失败: docType={}, docId={}, 耗时={}ms, error={} ==========",
                    event.getDocType(), event.getDocId(), totalTime, e.getMessage(), e);
            updateParseStatus(event.getDocType(), event.getDocId(), "FAILED");
        }
    }

    /**
     * 检查文档是否已被逻辑删除（并发保护 + 事务提交等待）
     * 如果记录不存在，等待事务提交后重试（最多 5 次，共 1.5s）
     */
    private boolean isDeleted(String docType, Long docId) {
        for (int retry = 0; retry < 5; retry++) {
            try {
                if (MqConstants.DOC_TYPE_LITERATURE.equals(docType)) {
                    var lit = literatureMapper.selectById(docId);
                    if (lit != null) {
                        return lit.getDeleted() != null && lit.getDeleted() == 1;
                    }
                } else {
                    var doc = mdDocumentMapper.selectById(docId);
                    if (doc != null) {
                        return doc.getDeleted() != null && doc.getDeleted() == 1;
                    }
                }
                // 记录未找到 → 可能事务未提交，等待后重试
                if (retry < 4) {
                    log.debug("记录未找到，等待事务提交后重试: docType={}, docId={}, retry={}", docType, docId, retry);
                    Thread.sleep(300);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.warn("检查删除状态异常: docType={}, docId={}", docType, docId, e);
                return false;
            }
        }
        // 5 次重试后仍未找到，视为已删除
        log.warn("重试5次后仍未找到记录，视为已删除: docType={}, docId={}", docType, docId);
        return true;
    }

    /**
     * 更新解析状态
     */
    private void updateParseStatus(String docType, Long docId, String status) {
        try {
            if (MqConstants.DOC_TYPE_LITERATURE.equals(docType)) {
                var lit = literatureMapper.selectById(docId);
                if (lit != null) {
                    lit.setParseStatus(status);
                    literatureMapper.updateById(lit);
                }
            } else {
                var doc = mdDocumentMapper.selectById(docId);
                if (doc != null) {
                    doc.setParseStatus(status);
                    mdDocumentMapper.updateById(doc);
                }
            }
        } catch (Exception e) {
            log.error("更新parseStatus失败: docType={}, docId={}, status={}", docType, docId, status, e);
        }
    }

    private String getTitle(String docType, Long docId) {
        try {
            if (MqConstants.DOC_TYPE_LITERATURE.equals(docType)) {
                var lit = literatureMapper.selectById(docId);
                return lit != null ? lit.getTitle() : "unknown";
            } else {
                var doc = mdDocumentMapper.selectById(docId);
                return doc != null ? doc.getTitle() : "unknown";
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getFileType(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "unknown";
    }
}