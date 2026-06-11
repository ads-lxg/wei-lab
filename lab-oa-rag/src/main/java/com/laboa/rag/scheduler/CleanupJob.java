package com.laboa.rag.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.constant.MqConstants;
import com.laboa.doc.entity.MdDocument;
import com.laboa.doc.mapper.MdDocumentMapper;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.mapper.MinioFileMapper;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.mapper.LiteratureMapper;
import com.laboa.rag.service.ResourceTextService;
import com.laboa.rag.service.VectorStoreService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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

    /**
     * 每日凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOrphanData() {
        log.info("========== 开始补偿清理孤儿文件/索引 ==========");

        cleanupOrphanLiterature();
        cleanupOrphanDoc();

        log.info("========== 补偿清理完成 ==========");
    }

    /**
     * 清理已删除文献的 MinIO 文件和 ES 索引
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
                // 删除 MinIO 文件
                if (lit.getFileId() != null) {
                    MinioFile mf = minioFileMapper.selectById(lit.getFileId());
                    if (mf != null) {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(mf.getBucket())
                                .object(mf.getStoredName())
                                .build());
                        minioFileMapper.deleteById(lit.getFileId());
                    }
                }
                log.info("补偿清理文献完成: literatureId={}", lit.getId());
            } catch (Exception e) {
                log.error("补偿清理文献失败: literatureId={}, error={}", lit.getId(), e.getMessage());
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
}