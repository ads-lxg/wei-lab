package com.laboa.literature.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laboa.common.constant.MqConstants;
import com.laboa.common.entity.OutboxEvent;
import com.laboa.common.event.DocumentCreatedEvent;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.exception.ErrorCode;
import com.laboa.common.mapper.OutboxEventMapper;
import com.laboa.common.result.PageResult;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import com.laboa.literature.dto.*;
import com.laboa.literature.entity.DownloadLog;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.entity.RagFolder;
import com.laboa.literature.mapper.DownloadLogMapper;
import com.laboa.literature.mapper.LiteratureMapper;
import com.laboa.literature.mapper.RagFolderMapper;
import com.laboa.literature.service.DocumentManagementService;
import com.laboa.literature.vo.BatchUploadResultVO;
import com.laboa.literature.vo.LiteratureDetailVO;
import com.laboa.literature.vo.LiteratureListItemVO;
import com.laboa.literature.vo.LiteratureRecycleVO;
import com.laboa.search.service.SearchResult;
import com.laboa.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 文献管理服务实现
 * <p>
 * 核心设计：
 * - 文献必须归属于某个目录（folderId）
 * - 删除采用逻辑删除（deleted=1进入回收站），彻底删除才物理删除
 * - 上传调用已有FileService，不重复实现上传逻辑
 * - 批量上传通过Excel解析元数据+文件名匹配实现
 * - 下载次数统计支持单文件和批量下载
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentManagementServiceImpl implements DocumentManagementService {

    private final LiteratureMapper literatureMapper;
    private final DownloadLogMapper downloadLogMapper;
    private final RagFolderMapper ragFolderMapper;
    private final FileService fileService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventMapper outboxEventMapper;
    private final SearchService searchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 上传文献 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LiteratureDetailVO upload(DocumentUploadDTO dto, MultipartFile file, Long uploaderId) {
        // 1. 校验目录存在
        checkFolderExist(dto.getFolderId());

        // 2. 读取文件字节（MultipartFile在请求结束后会被清理）
        byte[] fileBytes;
        String originalName = file.getOriginalFilename();
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UPLOAD_ERROR);
        }

        // 3. 调用已有文件上传组件
        MinioFile minioFile = fileService.uploadFile(file, uploaderId);
        Long fileId = minioFile.getId();

        // 4. 创建文献记录
        Literature literature = new Literature();
        literature.setTitle(dto.getTitle() != null ? dto.getTitle() : getBaseName(originalName));
        literature.setAuthors(dto.getAuthors());
        literature.setAbstractText(dto.getAbstractText());
        literature.setKeywords(dto.getKeywords());
        literature.setPublishDate(dto.getPublishDate());
        literature.setSourceJournal(dto.getSourceJournal());
        literature.setDoi(dto.getDoi());
        literature.setFolderId(dto.getFolderId());
        literature.setFileId(fileId);
        literature.setFileType(getFileExtension(originalName));
        literature.setFileName(originalName);
        literature.setUploaderId(uploaderId);
        literature.setPermissionLevel(1);
        literature.setViewCount(0);
        literature.setDownloadCount(0);
        literature.setRagSource(dto.getRagSource() != null ? dto.getRagSource() : 1);
        literature.setParseStatus("PENDING");
        literatureMapper.insert(literature);

        // 5. 事务提交后异步发布文档创建事件（ragSource=0/1 都需要解析全文并存入ES）
        {
            final Long docId = literature.getId();
            final Long finalFileId = fileId;
            final String finalOriginalName = originalName;
            final boolean isRagSource = literature.getRagSource() == 1;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> {
                        eventPublisher.publishEvent(new DocumentCreatedEvent(
                                DocumentManagementServiceImpl.this, MqConstants.DOC_TYPE_LITERATURE, docId, finalFileId,
                                finalOriginalName, isRagSource, fileBytes
                        ));
                        log.info("DocumentCreatedEvent已发布: docId={}, ragSource={}", docId, isRagSource);
                    });
                }
            });
        }

        log.info("文献上传成功: id={}, title={}, folderId={}", literature.getId(), literature.getTitle(), dto.getFolderId());
        return toDetailVO(literature);
    }

    // ==================== 批量上传（Excel+文件） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchUploadResultVO batchUpload(Long folderId, MultipartFile excelFile, List<MultipartFile> files, Long uploaderId) {
        // 1. 校验目录存在
        checkFolderExist(folderId);

        // 2. 解析Excel元数据
        Map<String, ExcelRow> excelData = parseExcel(excelFile);

        // 3. 构建文件名->MultipartFile映射
        Map<String, MultipartFile> fileMap = new HashMap<>();
        for (MultipartFile f : files) {
            fileMap.put(f.getOriginalFilename(), f);
        }

        int successCount = 0;
        List<BatchUploadResultVO.FailItem> failList = new ArrayList<>();
        List<Long> successIds = new ArrayList<>();

        // 4. 遍历Excel行，匹配文件并创建文献
        for (Map.Entry<String, ExcelRow> entry : excelData.entrySet()) {
            String fileName = entry.getKey();
            ExcelRow row = entry.getValue();
            MultipartFile file = fileMap.get(fileName);

            if (file == null) {
                failList.add(new BatchUploadResultVO.FailItem(fileName, "未找到匹配的文件"));
                continue;
            }

            try {
                // 上传文件到MinIO
                MinioFile minioFile = fileService.uploadFile(file, uploaderId);

                // 创建文献记录
                Literature literature = new Literature();
                literature.setTitle(row.title != null ? row.title : getBaseName(fileName));
                literature.setAuthors(row.authors);
                literature.setAbstractText(row.abstractText);
                literature.setKeywords(row.keywords);
                literature.setPublishDate(row.publishDate);
                literature.setSourceJournal(row.sourceJournal);
                literature.setFolderId(folderId);
                literature.setFileId(minioFile.getId());
                literature.setFileType(getFileExtension(fileName));
                literature.setFileName(fileName);
                literature.setUploaderId(uploaderId);
                literature.setPermissionLevel(1);
                literature.setViewCount(0);
                literature.setDownloadCount(0);
                literature.setRagSource(row.ragSource);
                literature.setParseStatus("PENDING");
                literatureMapper.insert(literature);

                // 事务提交后异步发布文档创建事件（ragSource=0/1 都需要解析全文并存入ES）
                {
                    final Long docId = literature.getId();
                    final Long finalFileId = minioFile.getId();
                    final String finalOriginalName = fileName;
                    final boolean isRagSource = literature.getRagSource() == 1;
                    byte[] fileBytes = file.getBytes();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            CompletableFuture.runAsync(() -> {
                                eventPublisher.publishEvent(new DocumentCreatedEvent(
                                        DocumentManagementServiceImpl.this, MqConstants.DOC_TYPE_LITERATURE, docId, finalFileId,
                                        finalOriginalName, isRagSource, fileBytes
                                ));
                            });
                        }
                    });
                }

                successCount++;
                successIds.add(literature.getId());
            } catch (Exception e) {
                log.error("批量上传-单文件处理失败: fileName={}", fileName, e);
                failList.add(new BatchUploadResultVO.FailItem(fileName, "处理失败: " + e.getMessage()));
            }
        }

        // 5. 处理Excel中未提及但上传了的文件（无元数据，仅用文件名创建）
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            if (!excelData.containsKey(fileName)) {
                try {
                    MinioFile minioFile = fileService.uploadFile(file, uploaderId);
                    Literature literature = new Literature();
                    literature.setTitle(getBaseName(fileName));
                    literature.setFolderId(folderId);
                    literature.setFileId(minioFile.getId());
                    literature.setFileType(getFileExtension(fileName));
                    literature.setFileName(fileName);
                    literature.setUploaderId(uploaderId);
                    literature.setPermissionLevel(1);
                    literature.setViewCount(0);
                    literature.setDownloadCount(0);
                    literature.setRagSource(0);
                    literature.setParseStatus("PENDING");
                    literatureMapper.insert(literature);

                    // 事务提交后异步发布文档创建事件
                    {
                        final Long docId = literature.getId();
                        final Long finalFileId = minioFile.getId();
                        final String finalOriginalName = fileName;
                        final boolean isRagSource = literature.getRagSource() == 1;
                        byte[] fileBytes = file.getBytes();
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                CompletableFuture.runAsync(() -> {
                                    eventPublisher.publishEvent(new DocumentCreatedEvent(
                                            DocumentManagementServiceImpl.this, MqConstants.DOC_TYPE_LITERATURE, docId, finalFileId,
                                            finalOriginalName, isRagSource, fileBytes
                                    ));
                                });
                            }
                        });
                    }

                    successCount++;
                    successIds.add(literature.getId());
                } catch (Exception e) {
                    log.error("批量上传-无元数据文件处理失败: fileName={}", fileName, e);
                    failList.add(new BatchUploadResultVO.FailItem(fileName, "处理失败: " + e.getMessage()));
                }
            }
        }

        log.info("批量上传完成: folderId={}, success={}, fail={}", folderId, successCount, failList.size());
        return BatchUploadResultVO.builder()
                .successCount(successCount)
                .failCount(failList.size())
                .failList(failList)
                .successIds(successIds)
                .build();
    }

    // ==================== 删除文献（逻辑删除→回收站） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long documentId, Long operatorId) {
        // 1. 查询文献（包含已逻辑删除的，用selectById无法查到已删除的，所以用带deleted条件的查询）
        Literature literature = literatureMapper.selectById(documentId);
        if (literature == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_EXIST);
        }
        if (literature.getDeleted() != null && literature.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_DELETED);
        }

        // 2. 逻辑删除（MyBatis Plus自动处理deleted字段）
        literatureMapper.deleteById(documentId);

        // 3. 写入outbox事件（用于RAG向量库清理等）
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "resourceId", documentId,
                    "docType", MqConstants.DOC_TYPE_LITERATURE,
                    "fileId", literature.getFileId() != null ? literature.getFileId() : 0
            ));
            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(documentId);
            event.setEventType(MqConstants.EVENT_DELETE_RESOURCE);
            event.setPayload(payload);
            event.setStatus(MqConstants.OUTBOX_STATUS_PENDING);
            event.setRetryCount(0);
            event.setDocType(MqConstants.DOC_TYPE_LITERATURE);
            outboxEventMapper.insert(event);
        } catch (Exception e) {
            log.error("写入outbox失败: documentId={}", documentId, e);
            throw new BusinessException("删除失败");
        }

        log.info("文献删除(进入回收站): id={}, operatorId={}", documentId, operatorId);
    }

    // ==================== 批量删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> documentIds, Long operatorId) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_BATCH_EMPTY);
        }
        for (Long id : documentIds) {
            delete(id, operatorId);
        }
    }

    // ==================== 移动文献 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long documentId, Long targetFolderId) {
        // 1. 校验文献存在且未删除
        Literature literature = literatureMapper.selectById(documentId);
        if (literature == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_EXIST);
        }

        // 2. 校验目标目录存在
        checkFolderExist(targetFolderId);

        // 3. 更新folderId
        literature.setFolderId(targetFolderId);
        literatureMapper.updateById(literature);

        log.info("文献移动: id={}, from={}, to={}", documentId, literature.getFolderId(), targetFolderId);
    }

    // ==================== 批量移动 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMove(List<Long> documentIds, Long targetFolderId) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_BATCH_EMPTY);
        }
        checkFolderExist(targetFolderId);

        LambdaUpdateWrapper<Literature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(Literature::getId, documentIds)
                .eq(Literature::getDeleted, 0)
                .set(Literature::getFolderId, targetFolderId);
        literatureMapper.update(wrapper);

        log.info("批量移动文献: ids={}, targetFolderId={}", documentIds, targetFolderId);
    }

    // ==================== 下载文献 ====================

    @Override
    public String download(Long documentId, Long userId) {
        Literature literature = literatureMapper.selectById(documentId);
        if (literature == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_EXIST);
        }
        if (literature.getFileId() == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_FILE_MISSING);
        }

        // 记录下载日志并增加下载次数
        recordDownload(documentId, userId);

        // 返回后端文件流代理URL（MinIO不暴露公网，通过后端流式传输）
        return "/api/file/" + literature.getFileId() + "/stream?download=true";
    }

    // ==================== 批量下载文件ID获取 ====================

    @Override
    public List<Long> getDownloadFileIds(List<Long> documentIds, Long userId) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_BATCH_EMPTY);
        }

        List<Literature> literatureList = literatureMapper.selectBatchIds(documentIds);
        List<Long> fileIds = new ArrayList<>();

        for (Literature literature : literatureList) {
            if (literature.getFileId() != null) {
                fileIds.add(literature.getFileId());
                // 批量下载也增加下载次数
                recordDownload(literature.getId(), userId);
            }
        }

        return fileIds;
    }

    // ==================== 查询目录下文献 ====================

    @Override
    public PageResult<LiteratureListItemVO> listByFolder(DocumentFolderQueryDTO dto) {
        Page<Literature> page = new Page<>(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();

        // 目录过滤
        if (dto.getFolderId() != null) {
            wrapper.eq(Literature::getFolderId, dto.getFolderId());
        }

        // 关键词搜索（文件名、标题、作者、关键词）
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            wrapper.and(w -> w
                    .like(Literature::getFileName, dto.getKeyword())
                    .or().like(Literature::getTitle, dto.getKeyword())
                    .or().like(Literature::getAuthors, dto.getKeyword())
                    .or().like(Literature::getKeywords, dto.getKeyword())
            );
        }

        // 排序
        applySort(wrapper, dto.getSortField(), dto.getSortOrder());

        IPage<Literature> result = literatureMapper.selectPage(page, wrapper);
        List<LiteratureListItemVO> voList = result.getRecords().stream()
                .map(this::toListItemVO)
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), voList);
    }

    // ==================== 文献详情 ====================

    @Override
    public LiteratureDetailVO getDetail(Long documentId) {
        Literature literature = literatureMapper.selectById(documentId);
        if (literature == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_EXIST);
        }

        // 增加浏览次数
        incrementViewCount(documentId);

        return toDetailVO(literature);
    }

    // ==================== 文献搜索 ====================

    @Override
    public PageResult<LiteratureListItemVO> search(DocumentSearchDTO dto) {
        String keyword = dto.getKeyword();

        // 如果没有关键词，降级为MySQL分页查询
        if (keyword == null || keyword.isBlank()) {
            Page<Literature> page = new Page<>(dto.getPage(), dto.getSize());
            LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
            applySort(wrapper, dto.getSortField(), dto.getSortOrder());
            IPage<Literature> result = literatureMapper.selectPage(page, wrapper);
            List<LiteratureListItemVO> voList = result.getRecords().stream()
                    .map(this::toListItemVO)
                    .collect(Collectors.toList());
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), voList);
        }

        // 使用ES全文搜索（resource_text索引，非分块数据）
        try {
            SearchResult searchResult = searchService.search(keyword, "literature", dto.getPage(), dto.getSize());

            if (searchResult.getHits() == null || searchResult.getHits().isEmpty()) {
                return PageResult.of(0, dto.getPage(), dto.getSize(), List.of());
            }

            // 从ES结果中提取文献ID，回查MySQL获取完整信息
            List<Long> docIds = searchResult.getHits().stream()
                    .map(hit -> {
                        try {
                            return Long.parseLong(hit.getDocId());
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (docIds.isEmpty()) {
                return PageResult.of(searchResult.getTotal(), dto.getPage(), dto.getSize(), List.of());
            }

            // 批量查询文献，保持ES返回的排序
            List<Literature> literatures = literatureMapper.selectBatchIds(docIds);
            Map<Long, Literature> litMap = literatures.stream()
                    .collect(Collectors.toMap(Literature::getId, lit -> lit));

            // 按ES排序组装结果
            List<LiteratureListItemVO> voList = docIds.stream()
                    .filter(litMap::containsKey)
                    .map(id -> toListItemVO(litMap.get(id)))
                    .collect(Collectors.toList());

            return PageResult.of(searchResult.getTotal(), dto.getPage(), dto.getSize(), voList);
        } catch (Exception e) {
            log.warn("ES搜索异常，降级为MySQL搜索: keyword={}, error={}", keyword, e.getMessage());
            // 降级为MySQL LIKE搜索
            Page<Literature> page = new Page<>(dto.getPage(), dto.getSize());
            LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
            wrapper.and(w -> w
                    .like(Literature::getFileName, keyword)
                    .or().like(Literature::getTitle, keyword)
                    .or().like(Literature::getAuthors, keyword)
                    .or().like(Literature::getKeywords, keyword)
                    .or().like(Literature::getAbstractText, keyword)
            );
            applySort(wrapper, dto.getSortField(), dto.getSortOrder());
            IPage<Literature> result = literatureMapper.selectPage(page, wrapper);
            List<LiteratureListItemVO> voList = result.getRecords().stream()
                    .map(this::toListItemVO)
                    .collect(Collectors.toList());
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), voList);
        }
    }

    // ==================== 回收站 ====================

    @Override
    public PageResult<LiteratureRecycleVO> listRecycleBin(Integer page, Integer size) {
        // 回收站查询需要查询deleted=1的记录，MyBatis Plus默认不查逻辑删除的
        // 使用自定义SQL或绕过逻辑删除
        Page<Literature> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
        // 注意：由于MyBatis Plus的逻辑删除机制，selectPage会自动加deleted=0
        // 需要用自定义查询来获取deleted=1的记录
        IPage<Literature> result = literatureMapper.selectRecyclePage(pageParam);
        List<LiteratureRecycleVO> voList = result.getRecords().stream()
                .map(this::toRecycleVO)
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), voList);
    }

    // ==================== 恢复文献 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recover(Long documentId) {
        // 查询回收站中的文献（需要绕过逻辑删除）
        Literature literature = literatureMapper.selectRecycleById(documentId);
        if (literature == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_IN_RECYCLE);
        }

        // 恢复：将deleted设为0
        literatureMapper.recoverById(documentId);

        log.info("文献恢复: id={}", documentId);
    }

    // ==================== 批量恢复 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRecover(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_BATCH_EMPTY);
        }
        for (Long id : documentIds) {
            recover(id);
        }
    }

    // ==================== 彻底删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void permanentDelete(Long documentId) {
        // 1. 查询回收站中的文献
        Literature literature = literatureMapper.selectRecycleById(documentId);
        if (literature == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_IN_RECYCLE);
        }

        // 2. 写入outbox事件（用于清理ES索引：resource_text + doc_chunks）
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "resourceId", documentId,
                    "docType", MqConstants.DOC_TYPE_LITERATURE,
                    "fileId", literature.getFileId() != null ? literature.getFileId() : 0
            ));
            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(documentId);
            event.setEventType(MqConstants.EVENT_DELETE_RESOURCE);
            event.setPayload(payload);
            event.setStatus(MqConstants.OUTBOX_STATUS_PENDING);
            event.setRetryCount(0);
            event.setDocType(MqConstants.DOC_TYPE_LITERATURE);
            outboxEventMapper.insert(event);
        } catch (Exception e) {
            log.error("彻底删除-写入outbox失败: documentId={}", documentId, e);
            // 不抛异常，ES清理失败不应阻止彻底删除
        }

        // 3. 删除物理文件
        if (literature.getFileId() != null) {
            try {
                fileService.deleteFile(literature.getFileId());
            } catch (Exception e) {
                log.error("彻底删除-物理文件删除失败: fileId={}", literature.getFileId(), e);
            }
        }

        // 4. 物理删除数据库记录（绕过逻辑删除）
        literatureMapper.physicalDeleteById(documentId);

        log.info("文献彻底删除: id={}", documentId);
    }

    // ==================== 批量彻底删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchPermanentDelete(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_BATCH_EMPTY);
        }
        for (Long id : documentIds) {
            permanentDelete(id);
        }
    }

    // ==================== 解析状态查询 ====================

    @Override
    public String getParseStatus(Long documentId) {
        Literature literature = literatureMapper.selectById(documentId);
        if (literature == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_EXIST);
        }
        return literature.getParseStatus();
    }

    @Override
    public Map<Long, String> batchGetParseStatus(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        List<Literature> list = literatureMapper.selectBatchIds(documentIds);
        return list.stream().collect(Collectors.toMap(Literature::getId, lit -> {
            String status = lit.getParseStatus();
            return status != null ? status : "NONE";
        }));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验目录存在
     */
    private void checkFolderExist(Long folderId) {
        if (folderId == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_FOLDER_NOT_EXIST);
        }
        RagFolder folder = ragFolderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_FOLDER_NOT_EXIST);
        }
    }

    /**
     * 记录下载日志并增加下载次数
     */
    private void recordDownload(Long literatureId, Long userId) {
        DownloadLog downloadLog = new DownloadLog();
        downloadLog.setUserId(userId);
        downloadLog.setLiteratureId(literatureId);
        downloadLog.setDownloadTime(LocalDateTime.now());
        downloadLogMapper.insert(downloadLog);

        LambdaUpdateWrapper<Literature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Literature::getId, literatureId)
                .setSql("download_count = download_count + 1");
        literatureMapper.update(wrapper);
    }

    /**
     * 增加浏览次数
     */
    private void incrementViewCount(Long id) {
        LambdaUpdateWrapper<Literature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Literature::getId, id)
                .setSql("view_count = view_count + 1");
        literatureMapper.update(wrapper);
    }

    /**
     * 应用排序
     */
    private void applySort(LambdaQueryWrapper<Literature> wrapper, String sortField, String sortOrder) {
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        if ("downloadCount".equals(sortField)) {
            wrapper.orderBy(true, isAsc, Literature::getDownloadCount);
        } else {
            // 默认按创建时间排序
            wrapper.orderBy(true, isAsc, Literature::getCreateTime);
        }
    }

    /**
     * 解析Excel文件，返回文件名->元数据映射
     * Excel列：文件名、标题、作者、关键词、摘要、发表时间、来源期刊
     */
    private Map<String, ExcelRow> parseExcel(MultipartFile excelFile) {
        Map<String, ExcelRow> result = new LinkedHashMap<>();
        try (InputStream is = excelFile.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            // 跳过标题行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String fileName = getCellStringValue(row.getCell(0));
                if (fileName == null || fileName.isBlank()) continue;

                ExcelRow excelRow = new ExcelRow();
                excelRow.title = getCellStringValue(row.getCell(1));
                excelRow.authors = getCellStringValue(row.getCell(2));
                excelRow.keywords = getCellStringValue(row.getCell(3));
                excelRow.abstractText = getCellStringValue(row.getCell(4));

                Cell dateCell = row.getCell(5);
                if (dateCell != null) {
                    if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                        excelRow.publishDate = dateCell.getLocalDateTimeCellValue().toLocalDate();
                    } else {
                        String dateStr = getCellStringValue(dateCell);
                        if (dateStr != null && !dateStr.isBlank()) {
                            try {
                                excelRow.publishDate = LocalDate.parse(dateStr);
                            } catch (Exception e) {
                                log.warn("Excel日期解析失败: {}", dateStr);
                            }
                        }
                    }
                }

                excelRow.sourceJournal = getCellStringValue(row.getCell(6));

                // 第8列：RAG来源（0=仅全文检索, 1=全文检索+分块向量化），默认0
                String ragSourceStr = getCellStringValue(row.getCell(7));
                excelRow.ragSource = (ragSourceStr != null && "1".equals(ragSourceStr.trim())) ? 1 : 0;

                result.put(fileName, excelRow);
            }
        } catch (IOException e) {
            log.error("Excel解析失败", e);
            throw new BusinessException(ErrorCode.BATCH_UPLOAD_ERROR);
        }
        return result;
    }

    /**
     * 获取单元格字符串值
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                // 避免数字显示为科学计数法
                double numVal = cell.getNumericCellValue();
                if (numVal == Math.floor(numVal)) {
                    return String.valueOf((long) numVal);
                }
                return String.valueOf(numVal);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "unknown";
    }

    /**
     * 获取文件名（不含扩展名）
     */
    private String getBaseName(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        }
        return fileName;
    }

    /**
     * 获取目录名称
     */
    private String getFolderName(Long folderId) {
        if (folderId == null) return null;
        RagFolder folder = ragFolderMapper.selectById(folderId);
        return folder != null ? folder.getFolderName() : null;
    }

    // ==================== VO转换 ====================

    private LiteratureDetailVO toDetailVO(Literature lit) {
        LiteratureDetailVO vo = new LiteratureDetailVO();
        vo.setId(lit.getId());
        vo.setFileId(lit.getFileId());
        vo.setFileName(lit.getFileName());
        vo.setTitle(lit.getTitle());
        vo.setAuthors(lit.getAuthors());
        vo.setKeywords(lit.getKeywords());
        vo.setAbstractText(lit.getAbstractText());
        vo.setPublishDate(lit.getPublishDate());
        vo.setSourceJournal(lit.getSourceJournal());
        vo.setDoi(lit.getDoi());
        vo.setFolderId(lit.getFolderId());
        vo.setFolderName(getFolderName(lit.getFolderId()));
        vo.setFileType(lit.getFileType());
        vo.setUploaderId(lit.getUploaderId());
        vo.setCreateTime(lit.getCreateTime());
        vo.setDownloadCount(lit.getDownloadCount());
        vo.setViewCount(lit.getViewCount());
        vo.setRagSource(lit.getRagSource());
        vo.setParseStatus(lit.getParseStatus());
        return vo;
    }

    private LiteratureListItemVO toListItemVO(Literature lit) {
        LiteratureListItemVO vo = new LiteratureListItemVO();
        vo.setId(lit.getId());
        vo.setFileName(lit.getFileName());
        vo.setTitle(lit.getTitle());
        vo.setAuthors(lit.getAuthors());
        vo.setKeywords(lit.getKeywords());
        vo.setPublishDate(lit.getPublishDate());
        vo.setSourceJournal(lit.getSourceJournal());
        vo.setFolderId(lit.getFolderId());
        vo.setFolderName(getFolderName(lit.getFolderId()));
        vo.setFileType(lit.getFileType());
        vo.setCreateTime(lit.getCreateTime());
        vo.setDownloadCount(lit.getDownloadCount());
        vo.setParseStatus(lit.getParseStatus());
        return vo;
    }

    private LiteratureRecycleVO toRecycleVO(Literature lit) {
        LiteratureRecycleVO vo = new LiteratureRecycleVO();
        vo.setId(lit.getId());
        vo.setFileName(lit.getFileName());
        vo.setTitle(lit.getTitle());
        vo.setAuthors(lit.getAuthors());
        vo.setFolderId(lit.getFolderId());
        vo.setFolderName(getFolderName(lit.getFolderId()));
        vo.setDeleteTime(lit.getUpdateTime()); // 逻辑删除后updateTime即为删除时间
        vo.setCreateTime(lit.getCreateTime());
        return vo;
    }

    /**
     * Excel行数据内部类
     */
    private static class ExcelRow {
        String title;
        String authors;
        String keywords;
        String abstractText;
        LocalDate publishDate;
        String sourceJournal;
        int ragSource; // 第8列：0=仅全文检索, 1=全文检索+分块向量化
    }
}
