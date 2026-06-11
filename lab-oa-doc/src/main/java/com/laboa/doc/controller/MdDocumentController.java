package com.laboa.doc.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.event.DocumentCreatedEvent;
import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.doc.dto.DocPageDTO;
import com.laboa.doc.entity.MdDocument;
import com.laboa.doc.service.MdDocumentService;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class MdDocumentController {

    private final MdDocumentService mdDocumentService;
    private final FileService fileService;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<MdDocument> create(@RequestParam("title") String title, @RequestParam("file") MultipartFile file) {
        Long authorId = StpUtil.getLoginIdAsLong();

        // 先读取文件字节（MultipartFile 在请求结束后会被清理）
        byte[] fileBytes;
        try { fileBytes = file.getBytes(); } catch (Exception e) { return Result.error("读取文件失败"); }

        MinioFile minioFile = fileService.uploadFile(file, authorId);
        String fileType = getFileExtension(file.getOriginalFilename());
        MdDocument doc = mdDocumentService.create(minioFile.getId(), title, authorId, fileType);

        // 异步发布文档创建事件，携带文件字节 → 异步解析无需从 MinIO 回下载
        // 使用 CompletableFuture 确保不阻塞 HTTP 响应
        final Long finalDocId = doc.getId();
        final Long finalFileId = minioFile.getId();
        final String finalFileName = file.getOriginalFilename();
        final byte[] finalFileBytes = fileBytes;
        CompletableFuture.runAsync(() -> {
            eventPublisher.publishEvent(new DocumentCreatedEvent(
                    this, "doc", finalDocId, finalFileId, finalFileName, true, finalFileBytes
            ));
        });

        return Result.success(doc);
    }

    @GetMapping("/page")
    public Result<PageResult<MdDocument>> page(@ModelAttribute DocPageDTO dto) {
        PageResult<MdDocument> pageResult = mdDocumentService.page(dto);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable("id") Long id) {
        MdDocument doc = mdDocumentService.getById(id);
        String presignedUrl = fileService.getPresignedUrl(doc.getFileId());
        Map<String, Object> result = new HashMap<>();
        result.put("doc", doc);
        result.put("url", presignedUrl);
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