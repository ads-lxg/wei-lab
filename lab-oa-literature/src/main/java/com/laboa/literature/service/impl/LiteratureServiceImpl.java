package com.laboa.literature.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laboa.common.constant.MqConstants;
import com.laboa.common.entity.OutboxEvent;
import com.laboa.common.event.DocumentCreatedEvent;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.mapper.OutboxEventMapper;
import com.laboa.common.result.PageResult;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import com.laboa.literature.dto.LiteraturePageDTO;
import com.laboa.literature.entity.DownloadLog;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.mapper.DownloadLogMapper;
import com.laboa.literature.mapper.LiteratureMapper;
import com.laboa.literature.service.LiteratureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureServiceImpl implements LiteratureService {

    private final LiteratureMapper literatureMapper;
    private final DownloadLogMapper downloadLogMapper;
    private final FileService fileService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Literature create(Literature literature, MultipartFile file, Long uploaderId) {
        // 先读取文件字节（MultipartFile 在请求结束后会被清理，必须提前读取）
        byte[] fileBytes;
        String originalName = file.getOriginalFilename();
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException("读取文件失败");
        }

        // 上传到 MinIO
        MinioFile minioFile = fileService.uploadFile(file, uploaderId);
        Long fileId = minioFile.getId();
        literature.setFileId(fileId);
        literature.setFileType(getFileExtension(originalName));
        literature.setUploaderId(uploaderId);
        if (literature.getViewCount() == null) {
            literature.setViewCount(0);
        }
        if (literature.getDownloadCount() == null) {
            literature.setDownloadCount(0);
        }
        if (literature.getRagSource() == null) {
            literature.setRagSource(0);
        }
        // ragSource=true 时，标记待解析
        if (literature.getRagSource() == 1) {
            literature.setParseStatus("PENDING");
        } else {
            literature.setParseStatus("NONE");
        }
        literatureMapper.insert(literature);

        // 异步发布文档创建事件（不阻塞上传响应）
        // 使用 CompletableFuture 确保 publishEvent 在独立线程执行，
        // 避免 Spring 事件分发机制阻塞 HTTP 响应
        final boolean isRagSource = literature.getRagSource() == 1;
        final Long finalDocId = literature.getId();
        final Long finalFileId = fileId;
        final String finalOriginalName = originalName;
        log.info("异步发布DocumentCreatedEvent: docId={}, fileId={}, ragSource={}, fileName={}, contentSize={}",
                finalDocId, finalFileId, isRagSource, finalOriginalName, fileBytes.length);
        CompletableFuture.runAsync(() -> {
            eventPublisher.publishEvent(new DocumentCreatedEvent(
                    this, MqConstants.DOC_TYPE_LITERATURE, finalDocId, finalFileId,
                    finalOriginalName, isRagSource, fileBytes
            ));
            log.info("DocumentCreatedEvent已发布: docId={}", finalDocId);
        });

        return literature;
    }

    @Override
    public PageResult<Literature> page(LiteraturePageDTO dto) {
        Page<Literature> page = new Page<>(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            wrapper.and(w -> w
                    .like(Literature::getTitle, dto.getKeyword())
                    .or()
                    .like(Literature::getAuthors, dto.getKeyword())
                    .or()
                    .like(Literature::getAbstractText, dto.getKeyword())
                    .or()
                    .like(Literature::getKeywords, dto.getKeyword())
            );
        }
        if (dto.getAuthor() != null && !dto.getAuthor().isBlank()) {
            wrapper.like(Literature::getAuthors, dto.getAuthor());
        }
        if (dto.getPublishYear() != null) {
            wrapper.eq(Literature::getPublishYear, dto.getPublishYear());
        }
        wrapper.orderByDesc(Literature::getCreateTime);
        IPage<Literature> result = literatureMapper.selectPage(page, wrapper);
        return PageResult.of(result);
    }

    @Override
    public Literature getById(Long id) {
        Literature literature = literatureMapper.selectById(id);
        if (literature == null) {
            throw new BusinessException("文献不存在");
        }
        incrementViewCount(id);
        return literature;
    }

    @Override
    public void update(Literature literature) {
        if (literature.getId() == null) {
            throw new BusinessException("文献ID不能为空");
        }
        Literature exist = literatureMapper.selectById(literature.getId());
        if (exist == null) {
            throw new BusinessException("文献不存在");
        }
        literatureMapper.updateById(literature);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Literature literature = literatureMapper.selectById(id);
        if (literature == null) {
            throw new BusinessException("文献不存在");
        }
        if (literature.getDeleted() != null && literature.getDeleted() == 1) {
            log.info("文献已删除，跳过: id={}", id);
            return;
        }

        // 在同一事务中：逻辑删除 + 写入 outbox
        literatureMapper.deleteById(id);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "resourceId", id,
                    "docType", MqConstants.DOC_TYPE_LITERATURE,
                    "fileId", literature.getFileId() != null ? literature.getFileId() : 0
            ));

            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(id);
            event.setEventType(MqConstants.EVENT_DELETE_RESOURCE);
            event.setPayload(payload);
            event.setStatus(MqConstants.OUTBOX_STATUS_PENDING);
            event.setRetryCount(0);
            event.setDocType(MqConstants.DOC_TYPE_LITERATURE);
            outboxEventMapper.insert(event);

            log.info("文献删除+outbox写入成功: literatureId={}, fileId={}", id, literature.getFileId());
        } catch (Exception e) {
            log.error("写入outbox失败，事务回滚: literatureId={}", id, e);
            throw new BusinessException("删除失败");
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "unknown";
    }

    @Override
    public void incrementViewCount(Long id) {
        LambdaUpdateWrapper<Literature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Literature::getId, id);
        wrapper.setSql("view_count = view_count + 1");
        literatureMapper.update(wrapper);
    }

    @Override
    public void recordDownload(Long literatureId, Long userId) {
        DownloadLog log = new DownloadLog();
        log.setUserId(userId);
        log.setLiteratureId(literatureId);
        log.setDownloadTime(LocalDateTime.now());
        downloadLogMapper.insert(log);

        LambdaUpdateWrapper<Literature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Literature::getId, literatureId);
        wrapper.setSql("download_count = download_count + 1");
        literatureMapper.update(wrapper);
    }
}