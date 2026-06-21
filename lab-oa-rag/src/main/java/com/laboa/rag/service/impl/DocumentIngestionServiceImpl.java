package com.laboa.rag.service.impl;

import com.laboa.rag.dto.DocumentChunkDTO;
import com.laboa.rag.service.DocumentIngestionService;
import com.laboa.rag.service.EmbeddingService;
import com.laboa.rag.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档入库服务实现
 * 1. 文本分割（递归字符分割器，chunk size 500，overlap 50）
 * 2. 向量化（调用阿里 Embedding API）
 * 3. 存入 ES doc_chunks 索引
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    @Override
    public void ingestText(String content, String fileName, String sourcePath, String docType, Long docId) {
        if (content == null || content.isBlank()) {
            log.warn("文档内容为空，跳过入库: fileName={}", fileName);
            return;
        }

        long start = System.currentTimeMillis();

        // 1. 分割文本
        List<String> chunks = splitText(content);
        long splitTime = System.currentTimeMillis() - start;
        log.info("[耗时] 文本分割: {}ms, chunks={}", splitTime, chunks.size());

        // 2. 批量向量化
        long embedStart = System.currentTimeMillis();
        List<float[]> embeddings = embeddingService.embedBatch(chunks);
        long embedTime = System.currentTimeMillis() - embedStart;
        log.info("[耗时] 向量化: {}ms, chunks={}", embedTime, chunks.size());

        // 检查向量是否有效（非零向量）
        // 如果 embedding API 不可用，会返回零向量，此时不应存入 ES（否则 KNN 搜索无法匹配）
        int validCount = 0;
        for (float[] emb : embeddings) {
            if (isNonZeroVector(emb)) validCount++;
        }
        if (validCount == 0) {
            log.warn("所有向量均为零向量（Embedding API 不可用），跳过 doc_chunks 入库: docType={}, docId={}。" +
                    "文档仍可通过 BM25 全文检索搜索，但跨语言语义检索暂不可用。", docType, docId);
            return;
        }
        if (validCount < chunks.size()) {
            log.warn("部分向量为零向量({}/{}), 仅存储有效向量的 chunk: docType={}, docId={}",
                    chunks.size() - validCount, chunks.size(), docType, docId);
        }

        // 3. 构建 DTO 并存储到 ES（跳过零向量的 chunk）
        long storeStart = System.currentTimeMillis();
        List<DocumentChunkDTO> chunkDTOs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            float[] emb = (i < embeddings.size()) ? embeddings.get(i) : null;
            // 跳过零向量或缺失的 embedding
            if (emb == null || !isNonZeroVector(emb)) {
                continue;
            }
            DocumentChunkDTO dto = new DocumentChunkDTO();
            dto.setId(UUID.nameUUIDFromBytes((docType + "-" + docId + "-chunk-" + i).getBytes()).toString());
            dto.setContent(chunks.get(i));
            dto.setEmbedding(emb);
            dto.setFileName(fileName);
            dto.setSourcePath(sourcePath);
            dto.setChunkIndex(i);
            dto.setDocType(docType);
            dto.setDocId(docId);
            chunkDTOs.add(dto);
        }

        if (chunkDTOs.isEmpty()) {
            log.warn("没有有效的向量 chunk 可存储，跳过 ES 入库: docType={}, docId={}", docType, docId);
            return;
        }
        vectorStoreService.storeChunks(chunkDTOs);
        long storeTime = System.currentTimeMillis() - storeStart;
        log.info("[耗时] ES存储: {}ms, chunks={}", storeTime, chunks.size());

        long totalTime = System.currentTimeMillis() - start;
        log.info("[耗时] ingestText总耗时: {}ms (分割={}ms, 向量化={}ms, ES存储={}ms)",
                totalTime, splitTime, embedTime, storeTime);
    }

    @Override
    public void ingestFile(String filePath, String docType, Long docId) {
        try {
            String content = Files.readString(Path.of(filePath));
            String fileName = Path.of(filePath).getFileName().toString();
            ingestText(content, fileName, filePath, docType, docId);
        } catch (IOException e) {
            log.error("读取文件失败: filePath={}, error={}", filePath, e.getMessage(), e);
        }
    }

    /**
     * 递归字符分割器
     * 按 chunkSize 分割，相邻 chunk 之间有 chunkOverlap 重叠
     */
    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        int textLength = text.length();
        int start = 0;

        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);

            // 尝试在句子边界处分割
            if (end < textLength) {
                int lastPeriod = text.lastIndexOf('。', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int splitPoint = Math.max(lastPeriod, lastNewline);
                // splitPoint 必须在 (start, end] 范围内才有效
                if (splitPoint > start && splitPoint <= end) {
                    end = splitPoint + 1;
                }
            }

            // 确保至少前进1个字符，防止死循环
            if (end <= start) {
                end = start + 1;
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            start = end;
        }

        return chunks;
    }

    /**
     * 检查向量是否为非零向量
     * Embedding API 不可用时会返回全零向量，这种向量无法用于 KNN 搜索
     */
    private boolean isNonZeroVector(float[] vec) {
        if (vec == null || vec.length == 0) return false;
        for (float v : vec) {
            if (v != 0f) return true;
        }
        return false;
    }
}
