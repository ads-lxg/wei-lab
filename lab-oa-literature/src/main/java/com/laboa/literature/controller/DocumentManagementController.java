package com.laboa.literature.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import com.laboa.literature.dto.*;
import com.laboa.literature.service.DocumentManagementService;
import com.laboa.literature.service.DoiParseService;
import com.laboa.literature.vo.BatchUploadResultVO;
import com.laboa.literature.vo.LiteratureDetailVO;
import com.laboa.literature.vo.LiteratureListItemVO;
import com.laboa.literature.vo.LiteratureRecycleVO;
import com.laboa.notification.service.NotificationService;
import com.laboa.system.mapper.SysUserMapper;
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
import java.time.LocalDate;
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
    private final NotificationService notificationService;
    private final SysUserMapper sysUserMapper;
    private final DoiParseService doiParseService;

    // ==================== 上传文献 ====================

    @Operation(summary = "上传文献", description = "上传单个文献文件，必须指定所属目录ID。权限：ADMIN/TEACHER/STUDENT")
    @SaCheckPermission("literature:upload")
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
            @Parameter(description = "是否RAG来源(0/1)") @RequestParam(value = "ragSource", defaultValue = "1") Integer ragSource,
            @Parameter(description = "文献文件", required = true) @RequestPart("file") MultipartFile file) {

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

    @Operation(summary = "批量上传文献", description = "通过Excel+文件批量上传文献。Excel包含：文件名、标题、作者、关键词、摘要、发表时间、来源期刊、DOI、RAG来源(0/1)。权限：ADMIN")
    @SaCheckPermission("literature:batchUpload")
    @PostMapping("/batch-upload")
    public Result<BatchUploadResultVO> batchUpload(
            @Parameter(description = "所属目录ID") @RequestParam("folderId") Long folderId,
            @Parameter(description = "Excel元数据文件", required = true) @RequestPart("excelFile") MultipartFile excelFile,
            @Parameter(description = "文献文件列表", required = true) @RequestPart("files") List<MultipartFile> files) {

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
    @SaCheckPermission("literature:recycle")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        Long operatorId = StpUtil.getLoginIdAsLong();
        documentManagementService.delete(id, operatorId);
        return Result.success();
    }

    // ==================== 批量删除 ====================

    @Operation(summary = "批量删除文献", description = "批量逻辑删除文献，进入回收站。权限：ADMIN")
    @DeleteMapping("/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody DocumentBatchDeleteDTO dto) {

        Long operatorId = StpUtil.getLoginIdAsLong();
        documentManagementService.batchDelete(dto.getDocumentIds(), operatorId);
        return Result.success();
    }

    // ==================== 移动文献 ====================

    @Operation(summary = "移动文献", description = "将文献移动到目标目录。权限：ADMIN")
    @SaCheckPermission("literature:folder")
    @PutMapping("/move")
    public Result<Void> move(@Valid @RequestBody DocumentMoveDTO dto) {

        documentManagementService.move(dto.getDocumentId(), dto.getTargetFolderId());
        return Result.success();
    }

    // ==================== 批量移动 ====================

    @Operation(summary = "批量移动文献", description = "批量将文献移动到目标目录。权限：ADMIN")
    @SaCheckPermission("literature:folder")
    @PutMapping("/batch-move")
    public Result<Void> batchMove(@Valid @RequestBody DocumentBatchMoveDTO dto) {

        documentManagementService.batchMove(dto.getDocumentIds(), dto.getTargetFolderId());
        return Result.success();
    }

    // ==================== 下载文献 ====================

    @Operation(summary = "下载文献", description = "获取文献下载链接，下载次数+1。权限：ADMIN/TEACHER/STUDENT")
    @SaCheckPermission("literature:download")
    @GetMapping("/{id}/download")
    public Result<String> download(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        Long userId = StpUtil.getLoginIdAsLong();
        String downloadUrl = documentManagementService.download(id, userId);
        return Result.success(downloadUrl);
    }

    @Operation(summary = "预览文献", description = "获取文献预览链接（PDF在线预览），不增加下载次数。权限：所有已登录用户")
    @GetMapping("/{id}/preview")
    public Result<String> preview(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        LiteratureDetailVO detail = documentManagementService.getDetail(id);
        if (detail == null || detail.getFileId() == null) {
            throw new BusinessException("文献文件不存在");
        }
        return Result.success("/api/file/" + detail.getFileId() + "/stream");
    }

    // ==================== 批量下载ZIP ====================

    @Operation(summary = "批量下载文献(ZIP)", description = "将多个文献打包为ZIP下载，边压缩边传输，不生成临时文件。权限：ADMIN/TEACHER/STUDENT")
    @SaCheckPermission("literature:download")
    @PostMapping("/batch-download")
    public void batchDownload(
            @Valid @RequestBody DocumentBatchDeleteDTO dto,
            HttpServletResponse response) {

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
                    // 直接从MinIO读取文件流（后端内部网络可访问）
                    try (InputStream is = fileService.getFileStream(fileId)) {
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
    @SaCheckPermission("literature:view")
    @GetMapping("/folder")
    public Result<PageResult<LiteratureListItemVO>> listByFolder(DocumentFolderQueryDTO dto) {

        PageResult<LiteratureListItemVO> result = documentManagementService.listByFolder(dto);
        return Result.success(result);
    }

    // ==================== 文献详情 ====================

    @Operation(summary = "文献详情", description = "获取文献完整详情信息。权限：所有登录用户")
    @SaCheckPermission("literature:view")
    @GetMapping("/{id}/detail")
    public Result<LiteratureDetailVO> getDetail(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        LiteratureDetailVO result = documentManagementService.getDetail(id);
        return Result.success(result);
    }

    // ==================== 文献搜索 ====================

    @Operation(summary = "文献搜索", description = "全局模糊搜索文献（文件名、标题、作者、关键词、摘要），支持分页和排序。权限：所有登录用户")
    @SaCheckPermission("literature:view")
    @GetMapping("/search")
    public Result<PageResult<LiteratureListItemVO>> search(DocumentSearchDTO dto) {

        PageResult<LiteratureListItemVO> result = documentManagementService.search(dto);
        return Result.success(result);
    }

    // ==================== 仪表盘统计 ====================

    @Operation(summary = "仪表盘统计", description = "获取文献库仪表盘统计数据：总文献数、今日新增、热门文献、最近上传。仅统计未删除且不在回收站的文献")
    @SaCheckPermission("literature:view")
    @GetMapping("/dashboard-stats")
    public Result<Map<String, Object>> dashboardStats() {
        return Result.success(documentManagementService.getDashboardStats());
    }

    // ==================== 回收站 ====================

    @Operation(summary = "回收站列表", description = "分页查询回收站中的文献。权限：ADMIN")
    @SaCheckPermission("literature:recycle")
    @GetMapping("/recycle-bin")
    public Result<PageResult<LiteratureRecycleVO>> listRecycleBin(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "10") Integer size) {

        PageResult<LiteratureRecycleVO> result = documentManagementService.listRecycleBin(page, size);
        return Result.success(result);
    }

    // ==================== 恢复文献 ====================

    @Operation(summary = "恢复文献", description = "从回收站恢复文献。权限：ADMIN")
    @SaCheckPermission("literature:recycle")
    @PutMapping("/{id}/recover")
    public Result<Void> recover(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        documentManagementService.recover(id);
        return Result.success();
    }

    // ==================== 批量恢复 ====================

    @Operation(summary = "批量恢复文献", description = "从回收站批量恢复文献。权限：ADMIN")
    @SaCheckPermission("literature:recycle")
    @PutMapping("/batch-recover")
    public Result<Void> batchRecover(@Valid @RequestBody DocumentBatchRecoverDTO dto) {

        documentManagementService.batchRecover(dto.getDocumentIds());
        return Result.success();
    }

    // ==================== 彻底删除 ====================

    @Operation(summary = "彻底删除文献", description = "物理删除文献记录和文件，不可恢复。权限：ADMIN")
    @SaCheckPermission("literature:recycle")
    @DeleteMapping("/{id}/permanent")
    public Result<Void> permanentDelete(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        documentManagementService.permanentDelete(id);
        return Result.success();
    }

    @Operation(summary = "批量彻底删除文献", description = "批量物理删除文献记录和文件，不可恢复。权限：ADMIN")
    @SaCheckPermission("literature:recycle")
    @DeleteMapping("/batch-permanent")
    public Result<Void> batchPermanentDelete(@Valid @RequestBody DocumentBatchDeleteDTO dto) {

        documentManagementService.batchPermanentDelete(dto.getDocumentIds());
        return Result.success();
    }

    // ==================== 解析状态轮询 ====================

    @Operation(summary = "查询文献解析状态", description = "前端上传后轮询此接口获取解析进度。返回: NONE/PENDING/SUCCESS/FAILED。权限：所有登录用户")
    @SaCheckPermission("literature:view")
    @GetMapping("/{id}/parse-status")
    public Result<String> getParseStatus(
            @Parameter(description = "文献ID", required = true) @PathVariable("id") Long id) {

        String status = documentManagementService.getParseStatus(id);
        return Result.success(status);
    }

    @Operation(summary = "批量查询文献解析状态", description = "批量获取文献解析进度，用于批量上传后轮询。权限：所有登录用户")
    @SaCheckPermission("literature:view")
    @PostMapping("/batch-parse-status")
    public Result<java.util.Map<Long, String>> batchGetParseStatus(
            @RequestBody java.util.List<Long> documentIds) {

        java.util.Map<Long, String> statusMap = documentManagementService.batchGetParseStatus(documentIds);
        return Result.success(statusMap);
    }

    // ==================== DOI 批量解析 ====================

    @Operation(summary = "DOI批量解析", description = "上传仅含DOI列的Excel，通过Crossref API解析出文献元数据，返回可直接用于批量上传的Excel。权限：ADMIN/TEACHER/STUDENT")
    @SaCheckPermission("literature:upload")
    @PostMapping("/batch-parse-doi")
    public void batchParseDoi(
            @Parameter(description = "包含DOI号的Excel文件（第一列为DOI号）", required = true)
            @RequestPart("file") MultipartFile file,
            HttpServletResponse response) {

        // 1. 读取Excel中的DOI列表
        List<String> dois = new java.util.ArrayList<>();
        try (InputStream is = file.getInputStream();
             org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(is)) {

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;
                String doi = getCellString(row.getCell(0));
                if (doi != null && !doi.isBlank()) {
                    dois.add(doi.trim());
                }
            }
        } catch (Exception e) {
            log.error("DOI Excel读取失败", e);
            throw new BusinessException("Excel读取失败: " + e.getMessage());
        }

        if (dois.isEmpty()) {
            throw new BusinessException("未在Excel中找到DOI号");
        }

        // 2. 批量查询DOI元数据
        List<DoiParseService.DoiInfo> results = doiParseService.parseBatch(dois);

        // 3. 生成输出Excel
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=doi_import_result.xlsx");

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook outWorkbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet outSheet = outWorkbook.createSheet("批量导入");

            // 表头
            org.apache.poi.ss.usermodel.Row header = outSheet.createRow(0);
            String[] headers = {"文件名", "标题", "作者", "关键词", "摘要", "发表时间", "来源期刊", "DOI", "RAG来源"};
            org.apache.poi.ss.usermodel.CellStyle headerStyle = outWorkbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = outWorkbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            int rowIdx = 1;
            for (DoiParseService.DoiInfo info : results) {
                if (!info.success()) continue;

                org.apache.poi.ss.usermodel.Row row = outSheet.createRow(rowIdx++);
                // 文件名 = 标题.pdf
                String title = info.title();
                String fileName = title.isBlank() ? "unknown.pdf" : title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".pdf";
                row.createCell(0).setCellValue(fileName);
                row.createCell(1).setCellValue(title);
                row.createCell(2).setCellValue(info.authors());
                row.createCell(3).setCellValue(info.keywords());
                row.createCell(4).setCellValue(info.abstractText());
                row.createCell(5).setCellValue(info.publishDate());
                row.createCell(6).setCellValue(info.sourceJournal());
                row.createCell(7).setCellValue(info.doi());
                row.createCell(8).setCellValue(1); // 默认RAG来源=1
            }

            // 未解析成功的DOI追加到末尾，仅填写DOI列
            for (DoiParseService.DoiInfo info : results) {
                if (info.success()) continue;
                String doi = info.doi();
                if (doi == null || doi.isBlank()) continue;
                org.apache.poi.ss.usermodel.Row row = outSheet.createRow(rowIdx++);
                row.createCell(7).setCellValue(doi);
            }

            // 调整列宽
            for (int i = 0; i < headers.length; i++) {
                outSheet.autoSizeColumn(i);
            }

            outWorkbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("DOI解析Excel生成失败", e);
            throw new BusinessException("Excel生成失败: " + e.getMessage());
        }
    }

    // ==================== 通知辅助方法 ====================

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

    /** Excel 单元格转字符串 */
    private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }
}
