package com.laboa.literature.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.constant.Constants;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import com.laboa.literature.dto.*;
import com.laboa.literature.service.DocumentManagementService;
import com.laboa.literature.vo.BatchUploadResultVO;
import com.laboa.literature.vo.LiteratureDetailVO;
import com.laboa.literature.vo.LiteratureListItemVO;
import com.laboa.literature.vo.LiteratureRecycleVO;
import com.laboa.notification.service.NotificationService;
import com.laboa.system.mapper.SysUserMapper;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文献管理控制器
 * <p>
 * 权限模型：
 * - ADMIN：全部权限
 * - TEACHER/STUDENT：上传、下载、批量下载、查询、详情、搜索
 * - GUEST：查询、详情、搜索
 */
@Tag(name = "文献管理", description = "文献的完整生命周期管理：上传、查询、搜索、移动、删除、回收站、下载等")
@Slf4j
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentManagementController {

    private final DocumentManagementService documentManagementService;
    private final FileService fileService;
    private final ElasticsearchClient esClient;
    private final NotificationService notificationService;
    private final SysUserMapper sysUserMapper;

    // ==================== 上传文献 ====================

    @Operation(summary = "上传文献", description = "上传单个文献文件，必须指定所属目录ID。权限：ADMIN/TEACHER/STUDENT")
    @PostMapping("/upload")
    public Result<LiteratureDetailVO> upload(
            @Parameter(description = "所属目录ID") @RequestParam("folderId") Long folderId,
            @Parameter(description = "标题") @RequestParam(value = "title", required = false) String title,
            @Parameter(description = "作者") @RequestParam(value = "authors", required = false) String authors,
            @Parameter(description = "关键词") @RequestParam(value = "keywords", required = false) String keywords,
            @Parameter(description = "摘要") @RequestParam(value = "abstractText", required = false) String abstractText,
            @Parameter(description = "发表日期(yyyy-MM-dd)") @RequestParam(value = "publishDate", required = false) String publishDate,
            @Parameter(description = "来源期刊") @RequestParam(value = "sourceJournal", required = false) String sourceJournal,
            @Parameter(description = "DOI号") @RequestParam(value = "doi", required = false) String doi,
            @Parameter(description = "是否RAG来源(0/1)") @RequestParam(value = "ragSource", defaultValue = "0") Integer ragSource,
            @Parameter(description = "文献文件", required = true) @RequestPart("file") MultipartFile file) {

        checkUploadPermission();

        DocumentUploadDTO dto = new DocumentUploadDTO();
        dto.setFolderId(folderId);
        dto.setTitle(title);
        dto.setAuthors(authors);
        dto.setKeywords(keywords);
        dto.setAbstractText(abstractText);
        dto.setSourceJournal(sourceJournal);
        dto.setDoi(doi);
        dto.setRagSource(ragSource);
        if (publishDate != null && !publishDate.isBlank()) {
            dto.setPublishDate(java.time.LocalDate.parse(publishDate));
        }

        Long uploaderId = StpUtil.getLoginIdAsLong();
        LiteratureDetailVO result = documentManagementService.upload(dto, file, uploaderId);

        // 通知其他非游客用户（排除上传者自己）
        String docTitle = result.getTitle() != null ? result.getTitle() : file.getOriginalFilename();
        try {
            String uploaderName = getUploaderRealName(uploaderId);
            List<Long> targetUserIds = queryNonGuestUserIdsExcluding(uploaderId);
            notificationService.sendNotificationBatch(
                    targetUserIds,
                    "新文献上传通知",
                    uploaderName + " 上传了新文献「" + docTitle + "」",
                    "LITERATURE_UPLOAD",
                    result.getId()
            );
        } catch (Exception e) {
            log.warn("发送文献上传通知失败: {}", e.getMessage());
        }

        return Result.success(result);
    }

    // ==================== 批量上传 ====================

    @Operation(summary = "批量上传文献", description = "通过Excel+文件批量上传文献。Excel包含：文件名、标题、作者、关键词、摘要、发表时间、来源期刊、RAG来源(0/1)。权限：ADMIN")
    @PostMapping("/batch-upload")
    public Result<BatchUploadResultVO> batchUpload(
            @Parameter(description = "所属目录ID") @RequestParam("folderId") Long folderId,
            @Parameter(description = "Excel元数据文件", required = true) @RequestPart("excelFile") MultipartFile excelFile,
            @Parameter(description = "文献文件列表", required = true) @RequestPart("files") List<MultipartFile> files) {

        checkAdminPermission();

        Long uploaderId = StpUtil.getLoginIdAsLong();
        BatchUploadResultVO result = documentManagementService.batchUpload(folderId, excelFile, files, uploaderId);

        // 通知其他非游客用户（排除上传者自己）
        try {
            String uploaderName = getUploaderRealName(uploaderId);
            List<Long> targetUserIds = queryNonGuestUserIdsExcluding(uploaderId);
            notificationService.sendNotificationBatch(
                    targetUserIds,
                    "文献批量上传通知",
                    uploaderName + " 批量上传了 " + result.getSuccessCount() + " 份文献",
                    "LITERATURE_BATCH_UPLOAD",
                    null
            );
        } catch (Exception e) {
            log.warn("发送文献批量上传通知失败: {}", e.getMessage());
        }

        return Result.success(result);
    }

    // ==================== 删除文献 ====================

    @Operation(summary = "删除文献", description = "逻辑删除文献，进入回收站。权限：ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        checkAdminPermission();

        Long operatorId = StpUtil.getLoginIdAsLong();
        documentManagementService.delete(id, operatorId);
        return Result.success();
    }

    // ==================== 批量删除 ====================

    @Operation(summary = "批量删除文献", description = "批量逻辑删除文献，进入回收站。权限：ADMIN")
    @DeleteMapping("/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody DocumentBatchDeleteDTO dto) {

        checkAdminPermission();

        Long operatorId = StpUtil.getLoginIdAsLong();
        documentManagementService.batchDelete(dto.getDocumentIds(), operatorId);
        return Result.success();
    }

    // ==================== 移动文献 ====================

    @Operation(summary = "移动文献", description = "将文献移动到目标目录。权限：ADMIN")
    @PutMapping("/move")
    public Result<Void> move(@Valid @RequestBody DocumentMoveDTO dto) {

        checkAdminPermission();

        documentManagementService.move(dto.getDocumentId(), dto.getTargetFolderId());
        return Result.success();
    }

    // ==================== 批量移动 ====================

    @Operation(summary = "批量移动文献", description = "批量将文献移动到目标目录。权限：ADMIN")
    @PutMapping("/batch-move")
    public Result<Void> batchMove(@Valid @RequestBody DocumentBatchMoveDTO dto) {

        checkAdminPermission();

        documentManagementService.batchMove(dto.getDocumentIds(), dto.getTargetFolderId());
        return Result.success();
    }

    // ==================== 下载文献 ====================

    @Operation(summary = "下载文献", description = "获取文献下载链接，下载次数+1。权限：ADMIN/TEACHER/STUDENT")
    @GetMapping("/{id}/download")
    public Result<String> download(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        checkDownloadPermission();

        Long userId = StpUtil.getLoginIdAsLong();
        String presignedUrl = documentManagementService.download(id, userId);
        return Result.success(presignedUrl);
    }

    // ==================== 批量下载ZIP ====================

    @Operation(summary = "批量下载文献(ZIP)", description = "将多个文献打包为ZIP下载，边压缩边传输，不生成临时文件。权限：ADMIN/TEACHER/STUDENT")
    @PostMapping("/batch-download")
    public void batchDownload(
            @Valid @RequestBody DocumentBatchDeleteDTO dto,
            HttpServletResponse response) {

        checkDownloadPermission();

        Long userId = StpUtil.getLoginIdAsLong();
        List<Long> fileIds = documentManagementService.getDownloadFileIds(dto.getDocumentIds(), userId);

        if (fileIds.isEmpty()) {
            throw new BusinessException("没有可下载的文件");
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=documents.zip");

        try (OutputStream os = response.getOutputStream();
             ZipOutputStream zos = new ZipOutputStream(os)) {

            for (Long fileId : fileIds) {
                MinioFile minioFile = fileService.getById(fileId);
                try {
                    // 获取MinIO文件的输入流
                    String presignedUrl = fileService.getPresignedUrl(fileId, 5);
                    java.net.URL url = new java.net.URL(presignedUrl);
                    try (InputStream is = url.openStream()) {
                        ZipEntry entry = new ZipEntry(minioFile.getOriginalName());
                        zos.putNextEntry(entry);
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                    }
                } catch (Exception e) {
                    log.error("ZIP打包-文件下载失败: fileId={}, name={}", fileId, minioFile.getOriginalName(), e);
                }
            }
            zos.finish();
        } catch (Exception e) {
            log.error("批量下载ZIP失败", e);
            throw new BusinessException("批量下载失败");
        }
    }

    // ==================== 查询目录下文献 ====================

    @Operation(summary = "查询目录下文献", description = "分页查询指定目录下的文献列表，支持关键词搜索。权限：所有登录用户")
    @GetMapping("/folder")
    public Result<PageResult<LiteratureListItemVO>> listByFolder(DocumentFolderQueryDTO dto) {

        checkLoginPermission();

        PageResult<LiteratureListItemVO> result = documentManagementService.listByFolder(dto);
        return Result.success(result);
    }

    // ==================== 文献详情 ====================

    @Operation(summary = "文献详情", description = "获取文献完整详情信息。权限：所有登录用户")
    @GetMapping("/{id}/detail")
    public Result<LiteratureDetailVO> getDetail(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        checkLoginPermission();

        LiteratureDetailVO result = documentManagementService.getDetail(id);
        return Result.success(result);
    }

    // ==================== 文献搜索 ====================

    @Operation(summary = "文献搜索", description = "全局模糊搜索文献（文件名、标题、作者、关键词、摘要），支持分页和排序。权限：所有登录用户")
    @GetMapping("/search")
    public Result<PageResult<LiteratureListItemVO>> search(DocumentSearchDTO dto) {

        checkLoginPermission();

        PageResult<LiteratureListItemVO> result = documentManagementService.search(dto);
        return Result.success(result);
    }

    // ==================== 回收站 ====================

    @Operation(summary = "回收站列表", description = "分页查询回收站中的文献。权限：ADMIN")
    @GetMapping("/recycle-bin")
    public Result<PageResult<LiteratureRecycleVO>> listRecycleBin(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "10") Integer size) {

        checkAdminPermission();

        PageResult<LiteratureRecycleVO> result = documentManagementService.listRecycleBin(page, size);
        return Result.success(result);
    }

    // ==================== 恢复文献 ====================

    @Operation(summary = "恢复文献", description = "从回收站恢复文献。权限：ADMIN")
    @PutMapping("/{id}/recover")
    public Result<Void> recover(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        checkAdminPermission();

        documentManagementService.recover(id);
        return Result.success();
    }

    // ==================== 批量恢复 ====================

    @Operation(summary = "批量恢复文献", description = "从回收站批量恢复文献。权限：ADMIN")
    @PutMapping("/batch-recover")
    public Result<Void> batchRecover(@Valid @RequestBody DocumentBatchRecoverDTO dto) {

        checkAdminPermission();

        documentManagementService.batchRecover(dto.getDocumentIds());
        return Result.success();
    }

    // ==================== 彻底删除 ====================

    @Operation(summary = "彻底删除文献", description = "物理删除文献记录和文件，不可恢复。权限：ADMIN")
    @DeleteMapping("/{id}/permanent")
    public Result<Void> permanentDelete(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        checkAdminPermission();

        documentManagementService.permanentDelete(id);
        return Result.success();
    }

    @Operation(summary = "批量彻底删除文献", description = "批量物理删除文献记录和文件，不可恢复。权限：ADMIN")
    @DeleteMapping("/batch-permanent")
    public Result<Void> batchPermanentDelete(@Valid @RequestBody DocumentBatchDeleteDTO dto) {

        checkAdminPermission();

        documentManagementService.batchPermanentDelete(dto.getDocumentIds());
        return Result.success();
    }

    // ==================== 解析状态轮询 ====================

    @Operation(summary = "查询文献解析状态", description = "前端上传后轮询此接口获取解析进度。返回: NONE/PENDING/SUCCESS/FAILED。权限：所有登录用户")
    @GetMapping("/{id}/parse-status")
    public Result<String> getParseStatus(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        checkLoginPermission();

        String status = documentManagementService.getParseStatus(id);
        return Result.success(status);
    }

    @Operation(summary = "批量查询文献解析状态", description = "批量获取文献解析进度，用于批量上传后轮询。权限：所有登录用户")
    @PostMapping("/batch-parse-status")
    public Result<java.util.Map<Long, String>> batchGetParseStatus(
            @RequestBody java.util.List<Long> documentIds) {

        checkLoginPermission();

        java.util.Map<Long, String> statusMap = documentManagementService.batchGetParseStatus(documentIds);
        return Result.success(statusMap);
    }

    // ==================== ES验证接口（调试用） ====================

    @Operation(summary = "查看文献ES全文索引内容", description = "查看resource_text索引中指定文献的解析全文。权限：ADMIN")
    @GetMapping("/{id}/es-text")
    public Result<Map<String, Object>> getEsText(
            @Parameter(description = "文献ID") @PathVariable("id") Long id) {

        checkAdminPermission();

        try {
            GetResponse<Map> response = esClient.get(
                    GetRequest.of(g -> g.index("resource_text").id("literature_" + id)),
                    Map.class
            );
            if (response.found()) {
                return Result.success(response.source());
            }
            return Result.success(Map.of("found", false, "message", "ES中未找到该文献的全文索引"));
        } catch (Exception e) {
            return Result.success(Map.of("found", false, "error", e.getMessage()));
        }
    }

    @Operation(summary = "查看文献ES分块向量结果", description = "查看doc_chunks索引中指定文献的分块和向量信息。权限：ADMIN")
    @GetMapping("/{id}/es-chunks")
    public Result<Map<String, Object>> getEsChunks(
            @Parameter(description = "文献ID") @PathVariable("id") Long id,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "10") int size) {

        checkAdminPermission();

        try {
            // 使用 match 查询（兼容 long/keyword/text 类型），避免 term 查询因 mapping 不匹配而查不到
            SearchResponse<Map> response = esClient.search(
                    SearchRequest.of(s -> s
                            .index("doc_chunks")
                            .query(q -> q.match(m -> m.field("docId").query(id)))
                            .size(size)
                    ),
                    Map.class
            );
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("total", response.hits().total().value());
            List<Map<String, Object>> chunks = new java.util.ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    // 向量字段太大，只显示维度
                    if (source.containsKey("embedding")) {
                        Object emb = source.get("embedding");
                        if (emb instanceof List) {
                            source.put("embeddingDim", ((List<?>) emb).size());
                        }
                        source.remove("embedding");
                    }
                    source.put("_id", hit.id());
                    source.put("_score", hit.score());
                }
                chunks.add(source);
            }
            result.put("chunks", chunks);
            return Result.success(result);
        } catch (Exception e) {
            return Result.success(Map.of("found", false, "error", e.getMessage()));
        }
    }

    // ==================== 权限校验方法 ====================

    /**
     * 校验已登录（任何角色均可）
     */
    private void checkLoginPermission() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(401, "请先登录");
        }
    }

    /**
     * 校验上传权限：ADMIN / TEACHER / STUDENT
     */
    private void checkUploadPermission() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(401, "请先登录");
        }
        if (StpUtil.hasRole(Constants.ROLE_GUEST)) {
            throw new BusinessException(403, "游客无权上传文献");
        }
    }

    /**
     * 校验下载权限：ADMIN / TEACHER / STUDENT
     */
    private void checkDownloadPermission() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(401, "请先登录");
        }
        if (StpUtil.hasRole(Constants.ROLE_GUEST)) {
            throw new BusinessException(403, "游客无权下载文献");
        }
    }

    /**
     * 校验管理员权限
     */
    private void checkAdminPermission() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(401, "请先登录");
        }
        if (!StpUtil.hasRole(Constants.ROLE_ADMIN)) {
            throw new BusinessException(403, "仅管理员可执行此操作");
        }
    }

    /**
     * 查询所有非游客用户的ID列表
     */
    private List<Long> queryNonGuestUserIds() {
        return sysUserMapper.selectNonGuestUserIds();
    }

    /**
     * 查询非游客用户ID列表，排除指定用户（不给自己发通知）
     */
    private List<Long> queryNonGuestUserIdsExcluding(Long excludeUserId) {
        List<Long> all = sysUserMapper.selectNonGuestUserIds();
        all.remove(excludeUserId);
        return all;
    }

    /**
     * 获取上传者的真实姓名
     */
    private String getUploaderRealName(Long userId) {
        com.laboa.system.entity.SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user != null ? user.getUsername() : "未知用户";
    }
}
