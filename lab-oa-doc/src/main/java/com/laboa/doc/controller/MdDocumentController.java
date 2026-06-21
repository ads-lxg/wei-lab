package com.laboa.doc.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.event.DocumentCreatedEvent;
import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.common.search.DocIdValidator;
import com.laboa.doc.dto.DocPageDTO;
import com.laboa.doc.entity.MdDocument;
import com.laboa.doc.service.MdDocumentService;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import com.laboa.search.service.SearchHitVO;
import com.laboa.search.service.SearchResult;
import com.laboa.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class MdDocumentController {

    private final MdDocumentService mdDocumentService;
    private final FileService fileService;
    private final ApplicationEventPublisher eventPublisher;
    private final SearchService searchService;
    private final List<DocIdValidator> docIdValidators;

    @PostMapping
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<MdDocument> create(@RequestParam("title") String title, @RequestParam("file") MultipartFile file) {
        Long authorId = StpUtil.getLoginIdAsLong();

        // 先读取文件字节（MultipartFile 在请求结束后会被清理）
        byte[] fileBytes;
        try { fileBytes = file.getBytes(); } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage());
        }

        MinioFile minioFile = fileService.uploadFile(file, authorId);
        String fileType = getFileExtension(file.getOriginalFilename());
        MdDocument doc = mdDocumentService.create(minioFile.getId(), title, authorId, fileType);

        // 事务提交后异步发布文档创建事件
        final Long finalDocId = doc.getId();
        final Long finalFileId = minioFile.getId();
        final String finalFileName = file.getOriginalFilename();
        final byte[] finalFileBytes = fileBytes;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    eventPublisher.publishEvent(new DocumentCreatedEvent(
                            MdDocumentController.this, "doc", finalDocId, finalFileId, finalFileName, true, finalFileBytes
                    ));
                });
            }
        });

        return Result.success(doc);
    }

    /**
     * 在线创建文档（直接传入文本内容，无需上传文件）
     * 支持 md / txt 格式
     */
    @PostMapping("/online")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<MdDocument> createOnline(@RequestBody OnlineDocRequest request) {
        Long authorId = StpUtil.getLoginIdAsLong();

        String title = request.getTitle();
        String content = request.getContent();
        String fileType = request.getFileType();
        if (title == null || title.isBlank()) return Result.error("标题不能为空");
        if (content == null || content.isBlank()) return Result.error("内容不能为空");
        if (fileType == null || fileType.isBlank()) fileType = "md";
        if (!fileType.equals("md") && !fileType.equals("txt")) fileType = "md";

        // 将文本内容保存为文件到 MinIO
        String fileName = title + "." + fileType;
        byte[] fileBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MinioFile minioFile = fileService.uploadBytes(fileName, fileBytes, "text/" + (fileType.equals("md") ? "markdown" : "plain"), authorId);

        MdDocument doc = mdDocumentService.create(minioFile.getId(), title, authorId, fileType);

        // 事务提交后异步发布文档创建事件
        final Long finalDocId = doc.getId();
        final Long finalFileId = minioFile.getId();
        final byte[] finalFileBytes = fileBytes;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    eventPublisher.publishEvent(new DocumentCreatedEvent(
                            MdDocumentController.this, "doc", finalDocId, finalFileId, fileName, true, finalFileBytes
                    ));
                });
            }
        });

        return Result.success(doc);
    }

    /**
     * 在线创建文档请求
     */
    @lombok.Data
    public static class OnlineDocRequest {
        private String title;
        private String content;
        /** md / txt */
        private String fileType;
    }

    @GetMapping("/page")
    public Result<PageResult<MdDocument>> page(@ModelAttribute DocPageDTO dto) {
        PageResult<MdDocument> pageResult = mdDocumentService.page(dto);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable("id") Long id) {
        MdDocument doc = mdDocumentService.getById(id);
        // 使用流式代理URL（不暴露MinIO）
        String proxyUrl = "/api/file/" + doc.getFileId() + "/stream";
        String content = fileService.getFileContent(doc.getFileId());
        Map<String, Object> result = new HashMap<>();
        result.put("doc", doc);
        result.put("url", proxyUrl);
        result.put("content", content);
        return Result.success(result);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @RequestParam(value = "title", required = false) String title,
                               @RequestParam(value = "file", required = false) MultipartFile file) {
        Long fileId = null;
        if (file != null && !file.isEmpty()) {
            MinioFile minioFile = fileService.uploadFile(file, StpUtil.getLoginIdAsLong());
            fileId = minioFile.getId();
        }
        mdDocumentService.update(id, title, fileId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        mdDocumentService.delete(id);
        return Result.success();
    }

    /**
     * 内部文档全文检索（搜索 resource_text 索引中 docType=doc 的记录）
     * 过滤已删除的文档，确保只返回有效的文档
     */
    @GetMapping("/search")
    public Result<SearchResult> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        // 取较大候选窗口，在内存中完成过滤和分页
        int candidateSize = Math.max(size * 5, 100);
        SearchResult result = searchService.search(keyword, "doc", 1, candidateSize);

        if (result.getHits() == null || result.getHits().isEmpty()) {
            return Result.success(result);
        }

        // 过滤只保留 docType=doc 的结果
        List<SearchHitVO> docHits = result.getHits().stream()
                .filter(h -> "doc".equals(h.getDocType()))
                .collect(java.util.stream.Collectors.toList());

        // 使用 DocIdValidator 校验文档是否仍然存在（查找doc类型的validator）
        Set<String> docIds = docHits.stream()
                .map(SearchHitVO::getDocId)
                .collect(java.util.stream.Collectors.toSet());
        final Set<String> validDocIds = resolveValidDocIds(docIds);

        // 过滤掉已删除的文档
        List<SearchHitVO> filteredHits = docHits.stream()
                .filter(h -> validDocIds.contains(h.getDocId()))
                .collect(java.util.stream.Collectors.toList());

        // 重新分页
        int from = (page - 1) * size;
        int endIdx = Math.min(from + size, filteredHits.size());
        List<SearchHitVO> pageHits = from < filteredHits.size()
                ? filteredHits.subList(from, endIdx)
                : java.util.List.of();

        SearchResult filteredResult = new SearchResult(filteredHits.size(), pageHits);
        return Result.success(filteredResult);
    }

    private Set<String> resolveValidDocIds(Set<String> docIds) {
        for (DocIdValidator validator : docIdValidators) {
            if ("doc".equals(validator.getDocType())) {
                return validator.filterValidDocIds(docIds);
            }
        }
        // 没有找到validator，保留所有结果（兜底）
        return docIds;
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        mdDocumentService.updateStatus(id, status);
        return Result.success();
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "unknown";
    }
}