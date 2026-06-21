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
import com.laboa.system.mapper.SysUserMapper;
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
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 上传文献 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LiteratureDetailVO upload(DocumentUploadDTO dto, MultipartFile file, Long uploaderId) {
        // 1. 校验目录存在
        checkFolderExist(dto.getFolderId());

        // 2. 去重检查：DOI或文件名不能与已有文献重复
        checkDuplicate(dto.getDoi(), file.getOriginalFilename());

        // 3. 读取文件字节（MultipartFile在请求结束后会被清理）
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

        // 3. 构建文件名->MultipartFile映射（使用模糊匹配键）
        Map<String, MultipartFile> fileMap = new HashMap<>();
        for (MultipartFile f : files) {
            fileMap.put(f.getOriginalFilename(), f);
        }

        int successCount = 0;
        List<BatchUploadResultVO.FailItem> failList = new ArrayList<>();
        List<Long> successIds = new ArrayList<>();
        Set<String> matchedFileNames = new HashSet<>();

        // 4. 遍历Excel行，匹配文件并创建文献
        for (Map.Entry<String, ExcelRow> entry : excelData.entrySet()) {
            String excelFileName = entry.getKey();
            ExcelRow row = entry.getValue();

            // 使用模糊匹配：先精确匹配，再按前20字去标点匹配
            MultipartFile file = fileMap.get(excelFileName);
            if (file == null) {
                file = fuzzyMatchFile(excelFileName, fileMap, matchedFileNames);
            }

            if (file == null) {
                failList.add(buildFailItem(excelFileName, row, "未找到匹配的文件"));
                continue;
            }

            matchedFileNames.add(file.getOriginalFilename());

            try {
                // 去重检查：DOI或文件名不能与已有文献重复
                checkDuplicate(row.doi, file.getOriginalFilename());

                // 上传文件到MinIO
                MinioFile minioFile = fileService.uploadFile(file, uploaderId);

                // 创建文献记录
                Literature literature = new Literature();
                literature.setTitle(row.title != null ? row.title : getBaseName(file.getOriginalFilename()));
                literature.setAuthors(row.authors);
                literature.setAbstractText(row.abstractText);
                literature.setKeywords(row.keywords);
                literature.setPublishDate(row.publishDate);
                literature.setSourceJournal(row.sourceJournal);
                literature.setDoi(row.doi);
                literature.setFolderId(folderId);
                literature.setFileId(minioFile.getId());
                literature.setFileType(getFileExtension(file.getOriginalFilename()));
                literature.setFileName(file.getOriginalFilename());
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
                    final String finalOriginalName = file.getOriginalFilename();
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
            } catch (BusinessException e) {
                // 去重检查失败等业务异常
                log.warn("批量上传-业务校验失败: fileName={}, reason={}", file.getOriginalFilename(), e.getMessage());
                failList.add(buildFailItem(file.getOriginalFilename(), row, e.getMessage()));
            } catch (Exception e) {
                log.error("批量上传-单文件处理失败: fileName={}", file.getOriginalFilename(), e);
                failList.add(buildFailItem(file.getOriginalFilename(), row, "处理失败: " + e.getMessage()));
            }
        }

        // 5. 上传了但Excel中没有对应条目的文件 → 标记为失败（不再自动上传）
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            if (!matchedFileNames.contains(fileName)) {
                failList.add(buildFailItem(fileName, null, "Excel中未找到匹配的文件名记录"));
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

    /**
     * 模糊匹配文件名：去除标点后比较前20个字符
     * 先精确匹配，再模糊匹配
     */
    private MultipartFile fuzzyMatchFile(String excelFileName, Map<String, MultipartFile> fileMap, Set<String> alreadyMatched) {
        String normalizedExcel = normalizeFileName(excelFileName);

        for (Map.Entry<String, MultipartFile> entry : fileMap.entrySet()) {
            if (alreadyMatched.contains(entry.getKey())) continue;

            String normalizedFile = normalizeFileName(entry.getKey());
            if (normalizedExcel.equals(normalizedFile)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 标准化文件名：去除所有标点符号，取前20个字符，转小写
     */
    private String normalizeFileName(String fileName) {
        if (fileName == null) return "";
        // 去除扩展名
        String nameWithoutExt = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf("."))
                : fileName;
        // 去除所有标点符号（包括中文标点）
        String cleaned = nameWithoutExt.replaceAll("[\\p{Punct}\\s\\p{P}]", "");
        // 取前20个字符，转小写
        String truncated = cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
        return truncated.toLowerCase();
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

        // 如果没有关键词，降级为MySQL分页查询（仅返回目录树中存在的文献）
        if (keyword == null || keyword.isBlank()) {
            Page<Literature> page = new Page<>(dto.getPage(), dto.getSize());
            LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
            // 仅返回目录树中存在的文献（folderId 不为空且目录未删除）
            wrapper.isNotNull(Literature::getFolderId)
                   .inSql(Literature::getFolderId, "SELECT id FROM rag_folder WHERE deleted = 0");
            applySort(wrapper, dto.getSortField(), dto.getSortOrder());
            IPage<Literature> result = literatureMapper.selectPage(page, wrapper);
            List<LiteratureListItemVO> voList = result.getRecords().stream()
                    .map(this::toListItemVO)
                    .collect(Collectors.toList());
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), voList);
        }

        // 使用ES全文搜索，搜索类型限定为 literature
        // 注意：不能仅靠 ES 排除已删除文献，因为删除消息可能丢失导致索引残留
        // 最终有效性通过回查 MySQL 确认（@TableLogic 自动过滤 deleted=1）+ 目录存在性校验
        try {
            // 取足够多的候选结果，因为ES中可能存在已物理删除/回收站但索引未清理的残留数据
            // 一次性取较大候选窗口，在内存中完成准确分页和总数统计
            int candidateSize = Math.max(dto.getSize() * 20, 1000);
            SearchResult searchResult = searchService.search(keyword, "literature", 1, candidateSize, null, dto.getSearchMode());

            if (searchResult.getHits() == null || searchResult.getHits().isEmpty()) {
                // ES返回空，降级为MySQL LIKE搜索（避免ES索引未就绪/异常导致搜不到）
                log.info("ES搜索返回空，降级MySQL搜索: keyword={}", keyword);
                Page<Literature> page = new Page<>(dto.getPage(), dto.getSize());
                LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
                wrapper.isNotNull(Literature::getFolderId)
                       .inSql(Literature::getFolderId, "SELECT id FROM rag_folder WHERE deleted = 0")
                       .and(w -> w
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
                    .distinct()
                    .collect(Collectors.toList());

            if (docIds.isEmpty()) {
                // ES命中但无法解析docId，降级MySQL搜索
                log.info("ES命中但docId无法解析，降级MySQL搜索: keyword={}", keyword);
                Page<Literature> page = new Page<>(dto.getPage(), dto.getSize());
                LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
                wrapper.isNotNull(Literature::getFolderId)
                       .inSql(Literature::getFolderId, "SELECT id FROM rag_folder WHERE deleted = 0")
                       .and(w -> w
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

            // 批量查询文献（MyBatis-Plus @TableLogic 自动排除 deleted=1）
            List<Literature> literatures = literatureMapper.selectBatchIds(docIds);
            Map<Long, Literature> litMap = literatures.stream()
                    .collect(Collectors.toMap(Literature::getId, lit -> lit));

            // 批量查询所有有效目录ID（未删除的），用于过滤掉目录已被删除的孤儿文献
            Set<Long> validFolderIds = collectValidFolderIds(literatures);

            // 按ES排序组装结果
            // 过滤条件：
            //   1. MySQL 中存在（@TableLogic 已过滤 deleted=1）
            //   2. folderId 不为空（必须归属于某个目录）
            //   3. folderId 对应的目录存在且未删除（排除目录已删除的孤儿文献）
            List<LiteratureListItemVO> voList = searchResult.getHits().stream()
                    .map(hit -> {
                        try {
                            return Long.parseLong(hit.getDocId());
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(litMap::containsKey)
                    .map(litMap::get)
                    .filter(lit -> lit.getFolderId() != null && validFolderIds.contains(lit.getFolderId()))
                    .map(this::toListItemVO)
                    .collect(Collectors.toList());

            // 过滤后的实际有效文献数量作为 total
            long actualTotal = voList.size();

            // 内存分页截取
            int from = (dto.getPage() - 1) * dto.getSize();
            if (from >= voList.size()) {
                return PageResult.of(actualTotal, dto.getPage(), dto.getSize(), List.of());
            }
            int endIdx = Math.min(from + dto.getSize(), voList.size());
            List<LiteratureListItemVO> pageList = voList.subList(from, endIdx);

            return PageResult.of(actualTotal, dto.getPage(), dto.getSize(), pageList);
        } catch (Exception e) {
            log.warn("ES搜索异常，降级为MySQL搜索: keyword={}, error={}", keyword, e.getMessage());
            // 降级为MySQL LIKE搜索（自动排除已删除文献 + 仅返回目录树中的文献）
            Page<Literature> page = new Page<>(dto.getPage(), dto.getSize());
            LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
            wrapper.isNotNull(Literature::getFolderId)
                   .inSql(Literature::getFolderId, "SELECT id FROM rag_folder WHERE deleted = 0")
                   .and(w -> w
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

    /**
     * 收集文献列表对应的有效目录ID集合（目录未删除）
     */
    private Set<Long> collectValidFolderIds(List<Literature> literatures) {
        if (literatures == null || literatures.isEmpty()) return Collections.emptySet();
        Set<Long> folderIds = literatures.stream()
                .map(Literature::getFolderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (folderIds.isEmpty()) return Collections.emptySet();
        List<RagFolder> folders = ragFolderMapper.selectList(
                new LambdaQueryWrapper<RagFolder>()
                        .in(RagFolder::getId, folderIds)
                        .select(RagFolder::getId)
        );
        return folders.stream().map(RagFolder::getId).collect(Collectors.toSet());
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

        // 恢复后重新索引到ES（异步，事务提交后触发）
        Long docId = documentId;
        Long fileId = literature.getFileId();
        String fileName = literature.getFileName();
        boolean isRagSource = literature.getRagSource() != null && literature.getRagSource() == 1;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        if (fileId != null) {
                            // 从MinIO读取文件内容，重新触发解析和索引
                            try (java.io.InputStream is = fileService.getFileStream(fileId)) {
                                byte[] fileContent = is.readAllBytes();
                                eventPublisher.publishEvent(new DocumentCreatedEvent(
                                        DocumentManagementServiceImpl.this,
                                        MqConstants.DOC_TYPE_LITERATURE,
                                        docId, fileId, fileName, isRagSource, fileContent
                                ));
                                log.info("文献恢复后重新索引ES: id={}", docId);
                            }
                        }
                    } catch (Exception e) {
                        log.error("文献恢复后重新索引ES失败: id={}, error={}", docId, e.getMessage(), e);
                    }
                });
            }
        });

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

        // 2. 写入outbox事件（用于清理ES索引 + MinIO文件）
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "resourceId", documentId,
                    "docType", MqConstants.DOC_TYPE_LITERATURE,
                    "fileId", literature.getFileId() != null ? literature.getFileId() : 0,
                    "permanent", true
            ));
            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(documentId);
            event.setEventType(MqConstants.EVENT_PERMANENT_DELETE_RESOURCE);
            event.setPayload(payload);
            event.setStatus(MqConstants.OUTBOX_STATUS_PENDING);
            event.setRetryCount(0);
            event.setDocType(MqConstants.DOC_TYPE_LITERATURE);
            outboxEventMapper.insert(event);
        } catch (Exception e) {
            log.error("彻底删除-写入outbox失败: documentId={}", documentId, e);
            // 不抛异常，ES清理失败不应阻止彻底删除
        }

        // 3. 物理删除数据库记录（绕过逻辑删除，先删DB再异步删文件）
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
     * 去重检查：DOI或文件名不能与已有文献（未删除的）重复
     */
    private void checkDuplicate(String doi, String fileName) {
        // DOI去重（如果提供了DOI）
        if (doi != null && !doi.isBlank()) {
            Long doiCount = literatureMapper.selectCount(
                    new LambdaQueryWrapper<Literature>()
                            .eq(Literature::getDoi, doi.trim())
            );
            if (doiCount > 0) {
                throw new BusinessException("DOI已存在，不能上传重复文献: " + doi);
            }
        }
        // 文件名去重
        if (fileName != null && !fileName.isBlank()) {
            Long nameCount = literatureMapper.selectCount(
                    new LambdaQueryWrapper<Literature>()
                            .eq(Literature::getFileName, fileName.trim())
            );
            if (nameCount > 0) {
                throw new BusinessException("文件名已存在，不能上传重复文献: " + fileName);
            }
        }
    }

    /**
     * 构建批量上传失败项（包含完整元数据，方便导出Excel后重新上传）
     */
    private BatchUploadResultVO.FailItem buildFailItem(String fileName, ExcelRow row, String reason) {
        BatchUploadResultVO.FailItem item = new BatchUploadResultVO.FailItem();
        item.setFileName(fileName);
        item.setReason(reason);
        if (row != null) {
            item.setTitle(row.title);
            item.setAuthors(row.authors);
            item.setKeywords(row.keywords);
            item.setAbstractText(row.abstractText);
            item.setPublishDate(row.publishDate != null ? row.publishDate.toString() : null);
            item.setSourceJournal(row.sourceJournal);
            item.setDoi(row.doi);
            item.setRagSource(row.ragSource);
        }
        return item;
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

                // 第8列：DOI号
                excelRow.doi = getCellStringValue(row.getCell(7));

                // 第9列：RAG来源（0=仅全文检索, 1=全文检索+分块向量化），默认1
                String ragSourceStr = getCellStringValue(row.getCell(8));
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
        if (lit.getUploaderId() != null) {
            com.laboa.system.entity.SysUser uploader = sysUserMapper.selectById(lit.getUploaderId());
            if (uploader != null) {
                vo.setUploaderName(
                    uploader.getRealName() != null && !uploader.getRealName().isBlank()
                        ? uploader.getRealName()
                        : uploader.getUsername()
                );
            }
        }
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
        vo.setDoi(lit.getDoi());
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

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new java.util.HashMap<>();

        // 总文献数：仅统计未删除且存在于目录树中的文献（folderId不为空）
        long totalDocuments = literatureMapper.selectCount(new LambdaQueryWrapper<Literature>()
                .isNotNull(Literature::getFolderId));
        stats.put("totalDocuments", totalDocuments);

        // 今日新增（仅目录树中的文献）
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayNew = literatureMapper.selectCount(new LambdaQueryWrapper<Literature>()
                .isNotNull(Literature::getFolderId)
                .ge(Literature::getCreateTime, todayStart));
        stats.put("todayNewDocuments", todayNew);

        // 总下载量（仅目录树中的文献）
        List<Literature> allDocs = literatureMapper.selectList(new LambdaQueryWrapper<Literature>()
                .isNotNull(Literature::getFolderId)
                .select(Literature::getDownloadCount));
        long totalDownloads = allDocs.stream()
                .mapToLong(d -> d.getDownloadCount() != null ? d.getDownloadCount() : 0)
                .sum();
        stats.put("totalDownloads", totalDownloads);

        // 热门文献（按下载量排序，仅目录树中的文献）
        List<Literature> hotLit = literatureMapper.selectList(new LambdaQueryWrapper<Literature>()
                .isNotNull(Literature::getFolderId)
                .orderByDesc(Literature::getDownloadCount)
                .last("LIMIT 5"));
        stats.put("hotDocuments", hotLit.stream().map(this::toListItemVO).collect(Collectors.toList()));

        // 最近上传（按创建时间排序，仅目录树中的文献）
        List<Literature> recentLit = literatureMapper.selectList(new LambdaQueryWrapper<Literature>()
                .isNotNull(Literature::getFolderId)
                .orderByDesc(Literature::getCreateTime)
                .last("LIMIT 5"));
        stats.put("recentUploads", recentLit.stream().map(this::toListItemVO).collect(Collectors.toList()));

        return stats;
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
        String doi;      // 第8列：DOI号
        int ragSource;   // 第9列：0=仅全文检索, 1=全文检索+分块向量化
    }
}
