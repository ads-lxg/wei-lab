package com.laboa.doc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laboa.common.constant.Constants;
import com.laboa.common.constant.MqConstants;
import com.laboa.common.entity.OutboxEvent;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.mapper.OutboxEventMapper;
import com.laboa.common.result.PageResult;
import com.laboa.doc.dto.DocPageDTO;
import com.laboa.doc.entity.MdDocument;
import com.laboa.doc.mapper.MdDocumentMapper;
import com.laboa.doc.service.MdDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MdDocumentServiceImpl implements MdDocumentService {

    private final MdDocumentMapper mdDocumentMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MdDocument create(Long fileId, String title, Long authorId, String fileType) {
        MdDocument doc = new MdDocument();
        doc.setFileId(fileId);
        doc.setTitle(title);
        doc.setAuthorId(authorId);
        doc.setFileType(fileType);
        doc.setStatus(Constants.DOC_STATUS_DRAFT);
        // 内部文档强制作为 RAG 来源
        doc.setRagSource(1);
        doc.setParseStatus("PENDING");
        mdDocumentMapper.insert(doc);
        return doc;
    }

    @Override
    public PageResult<MdDocument> page(DocPageDTO dto) {
        Page<MdDocument> page = new Page<>(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<MdDocument> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            wrapper.like(MdDocument::getTitle, dto.getKeyword());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MdDocument::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(MdDocument::getCreateTime);
        IPage<MdDocument> result = mdDocumentMapper.selectPage(page, wrapper);
        return PageResult.of(result);
    }

    @Override
    public MdDocument getById(Long id) {
        MdDocument doc = mdDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        return doc;
    }

    @Override
    public void update(Long id, String title, Long fileId) {
        MdDocument doc = mdDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        if (title != null) {
            doc.setTitle(title);
        }
        if (fileId != null) {
            doc.setFileId(fileId);
        }
        mdDocumentMapper.updateById(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MdDocument doc = mdDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        if (doc.getDeleted() != null && doc.getDeleted() == 1) {
            log.info("文档已删除，跳过: id={}", id);
            return;
        }

        // 同一事务中：逻辑删除 + 写入 outbox
        mdDocumentMapper.deleteById(id);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "resourceId", id,
                    "docType", MqConstants.DOC_TYPE_DOC,
                    "fileId", doc.getFileId() != null ? doc.getFileId() : 0
            ));

            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(id);
            event.setEventType(MqConstants.EVENT_DELETE_RESOURCE);
            event.setPayload(payload);
            event.setStatus(MqConstants.OUTBOX_STATUS_PENDING);
            event.setRetryCount(0);
            event.setDocType(MqConstants.DOC_TYPE_DOC);
            outboxEventMapper.insert(event);

            log.info("文档删除+outbox写入成功: docId={}, fileId={}", id, doc.getFileId());
        } catch (Exception e) {
            log.error("写入outbox失败，事务回滚: docId={}", id, e);
            throw new BusinessException("删除失败");
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        MdDocument doc = mdDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        doc.setStatus(status);
        mdDocumentMapper.updateById(doc);
    }
}