package com.laboa.rag.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.constant.MqConstants;
import com.laboa.doc.entity.MdDocument;
import com.laboa.doc.mapper.MdDocumentMapper;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.mapper.MinioFileMapper;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.entity.RagFolder;
import com.laboa.literature.mapper.LiteratureMapper;
import com.laboa.literature.mapper.RagFolderMapper;
import com.laboa.rag.service.ResourceTextService;
import com.laboa.rag.service.VectorStoreService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 定时补偿任务 — 清理孤儿文件/索引
 * 低频执行（每日凌晨3点），处理极端情况下 Outbox 丢失导致的数据不一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJob {

    private final LiteratureMapper literatureMapper;
    private final MdDocumentMapper mdDocumentMapper;
    private final MinioFileMapper minioFileMapper;
    private final MinioClient minioClient;
    private final VectorStoreService vectorStoreService;
    private final ResourceTextService resourceTextService;
    private final RagFolderMapper ragFolderMapper;

    /**
     * 每日凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOrphanData() {
        log.info("========== 开始补偿清理孤儿文件/索引 ==========");

        cleanupOrphanLiterature();
        cleanupOrphanDoc();
        cleanupEsOrphanIndexes();
        cleanupOrphanFolderLiterature();

        log.info("========== 补偿清理完成 ==========");
    }

    /**
     * 清理已删除文献的 ES 索引（不删除MinIO文件，因为回收站恢复需要）
     */
    private void cleanupOrphanLiterature() {
        // 查询所有已逻辑删除的文献（只查 deleted=1 的记录）
        List<Literature> deletedList = literatureMapper.selectList(
                new LambdaQueryWrapper<Literature>()
                        .eq(Literature::getDeleted, 1)
        );

        for (Literature lit : deletedList) {
            try {
                // 删除 ES 向量索引
                vectorStoreService.deleteByDocId(MqConstants.DOC_TYPE_LITERATURE, lit.getId());
                // 删除 ES 文本索引
                resourceTextService.deleteByResourceId(lit.getId(), MqConstants.DOC_TYPE_LITERATURE);
                // 注意：不删除MinIO文件，回收站恢复时需要重新读取文件内容索引到ES
                log.info("补偿清理文献ES索引完成（保留MinIO文件）: literatureId={}", lit.getId());
            } catch (Exception e) {
                log.error("补偿清理文献ES索引失败: literatureId={}, error={}", lit.getId(), e.getMessage());
            }
        }
    }

    /**
     * 清理ES中已物理删除或已逻辑删除（回收站）的文档索引
     * 扫描 resource_text 和 doc_chunks 索引中的所有文档ID，检查MySQL中是否存在且未删除
     */
    private void cleanupEsOrphanIndexes() {
        try {
            // 1. 清理 resource_text 中已删除的文献
            cleanupResourceTextLiteratureOrphans();
            // 2. 清理 resource_text 中已删除的内部文档
            cleanupResourceTextDocOrphans();
            // 3. 清理 doc_chunks 中已删除的文献 chunk
            cleanupDocChunksLiteratureOrphans();
            // 4. 清理 doc_chunks 中已删除的内部文档 chunk
            cleanupDocChunksDocOrphans();
        } catch (Exception e) {
            log.error("扫描ES残留索引失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清理 resource_text 中 MySQL 不存在或已逻辑删除的文献残留索引
     */
    private void cleanupResourceTextLiteratureOrphans() {
        List<Long> esIds = resourceTextService.findAllResourceIds(MqConstants.DOC_TYPE_LITERATURE);
        for (Long resourceId : esIds) {
            Long count = literatureMapper.selectCount(
                    new LambdaQueryWrapper<Literature>()
                            .eq(Literature::getId, resourceId)
                            .eq(Literature::getDeleted, 0)
            );
            if (count == null || count == 0) {
                try {
                    resourceTextService.deleteByResourceId(resourceId, MqConstants.DOC_TYPE_LITERATURE);
                    log.info("清理文献的resource_text残留索引: resourceId={}", resourceId);
                } catch (Exception e) {
                    log.error("清理文献的resource_text残留索引失败: resourceId={}, error={}", resourceId, e.getMessage());
                }
            }
        }
    }

    /**
     * 清理 resource_text 中 MySQL 不存在或已逻辑删除的内部文档残留索引
     */
    private void cleanupResourceTextDocOrphans() {
        List<Long> esIds = resourceTextService.findAllResourceIds(MqConstants.DOC_TYPE_DOC);
        for (Long resourceId : esIds) {
            Long count = mdDocumentMapper.selectCount(
                    new LambdaQueryWrapper<MdDocument>()
                            .eq(MdDocument::getId, resourceId)
                            .eq(MdDocument::getDeleted, 0)
            );
            if (count == null || count == 0) {
                try {
                    resourceTextService.deleteByResourceId(resourceId, MqConstants.DOC_TYPE_DOC);
                    log.info("清理内部文档的resource_text残留索引: resourceId={}", resourceId);
                } catch (Exception e) {
                    log.error("清理内部文档的resource_text残留索引失败: resourceId={}, error={}", resourceId, e.getMessage());
                }
            }
        }
    }

    /**
     * 清理 doc_chunks 中 MySQL 不存在或已逻辑删除的文献残留 chunk
     */
    private void cleanupDocChunksLiteratureOrphans() {
        List<Long> esDocIds = vectorStoreService.findAllDocIds(MqConstants.DOC_TYPE_LITERATURE);
        for (Long docId : esDocIds) {
            Long count = literatureMapper.selectCount(
                    new LambdaQueryWrapper<Literature>()
                            .eq(Literature::getId, docId)
                            .eq(Literature::getDeleted, 0)
            );
            if (count == null || count == 0) {
                try {
                    vectorStoreService.deleteByDocId(MqConstants.DOC_TYPE_LITERATURE, docId);
                    log.info("清理文献的doc_chunks残留chunk: docId={}", docId);
                } catch (Exception e) {
                    log.error("清理文献的doc_chunks残留chunk失败: docId={}, error={}", docId, e.getMessage());
                }
            }
        }
    }

    /**
     * 清理 doc_chunks 中 MySQL 不存在或已逻辑删除的内部文档残留 chunk
     */
    private void cleanupDocChunksDocOrphans() {
        List<Long> esDocIds = vectorStoreService.findAllDocIds(MqConstants.DOC_TYPE_DOC);
        for (Long docId : esDocIds) {
            Long count = mdDocumentMapper.selectCount(
                    new LambdaQueryWrapper<MdDocument>()
                            .eq(MdDocument::getId, docId)
                            .eq(MdDocument::getDeleted, 0)
            );
            if (count == null || count == 0) {
                try {
                    vectorStoreService.deleteByDocId(MqConstants.DOC_TYPE_DOC, docId);
                    log.info("清理内部文档的doc_chunks残留chunk: docId={}", docId);
                } catch (Exception e) {
                    log.error("清理内部文档的doc_chunks残留chunk失败: docId={}, error={}", docId, e.getMessage());
                }
            }
        }
    }

    /**
     * 清理已删除内部文档的 MinIO 文件和 ES 索引
     */
    private void cleanupOrphanDoc() {
        List<MdDocument> deletedList = mdDocumentMapper.selectList(
                new LambdaQueryWrapper<MdDocument>()
                        .eq(MdDocument::getDeleted, 1)
        );

        for (MdDocument doc : deletedList) {
            try {
                // 删除 ES 向量索引
                vectorStoreService.deleteByDocId(MqConstants.DOC_TYPE_DOC, doc.getId());
                // 删除 ES 文本索引
                resourceTextService.deleteByResourceId(doc.getId(), MqConstants.DOC_TYPE_DOC);
                // 删除 MinIO 文件
                if (doc.getFileId() != null) {
                    MinioFile mf = minioFileMapper.selectById(doc.getFileId());
                    if (mf != null) {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(mf.getBucket())
                                .object(mf.getStoredName())
                                .build());
                        minioFileMapper.deleteById(doc.getFileId());
                    }
                }
                log.info("补偿清理文档完成: docId={}", doc.getId());
            } catch (Exception e) {
                log.error("补偿清理文档失败: docId={}, error={}", doc.getId(), e.getMessage());
            }
        }
    }

    /**
     * 清理孤儿文献：文献未删除(deleted=0)但其所属目录已被删除(deleted=1)或不存在
     * 这些文献在目录树中不可见，但可能仍被搜索和RAG检索到
     * 处理方式：将这些孤儿文献移入回收站（逻辑删除），并清理ES索引
     */
    private void cleanupOrphanFolderLiterature() {
        // 查询所有未删除的文献（含 folderId）
        List<Literature> allActive = literatureMapper.selectList(
                new LambdaQueryWrapper<Literature>()
                        .eq(Literature::getDeleted, 0)
                        .isNotNull(Literature::getFolderId)
        );
        if (allActive.isEmpty()) return;

        // 查询所有未删除的目录ID
        Set<Long> validFolderIds = ragFolderMapper.selectList(
                new LambdaQueryWrapper<RagFolder>()
                        .select(RagFolder::getId)
        ).stream().map(RagFolder::getId).collect(Collectors.toSet());

        // 找出目录已被删除的孤儿文献
        List<Literature> orphans = allActive.stream()
                .filter(lit -> !validFolderIds.contains(lit.getFolderId()))
                .collect(Collectors.toList());

        if (orphans.isEmpty()) {
            return;
        }

        log.warn("发现 {} 个孤儿文献（所属目录已删除），将移入回收站并清理ES索引", orphans.size());
        for (Literature lit : orphans) {
            try {
                // 逻辑删除文献（移入回收站）
                literatureMapper.deleteById(lit.getId());
                // 清理 ES 索引
                vectorStoreService.deleteByDocId(MqConstants.DOC_TYPE_LITERATURE, lit.getId());
                resourceTextService.deleteByResourceId(lit.getId(), MqConstants.DOC_TYPE_LITERATURE);
                log.info("孤儿文献已移入回收站并清理ES索引: literatureId={}, folderId={}", lit.getId(), lit.getFolderId());
            } catch (Exception e) {
                log.error("清理孤儿文献失败: literatureId={}, error={}", lit.getId(), e.getMessage());
            }
        }
    }
}