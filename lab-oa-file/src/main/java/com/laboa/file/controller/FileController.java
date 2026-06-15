package com.laboa.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.result.Result;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public Result<MinioFile> upload(@RequestParam("file") MultipartFile file) {
        Long uploaderId = StpUtil.getLoginIdAsLong();
        MinioFile minioFile = fileService.uploadFile(file, uploaderId);
        return Result.success(minioFile);
    }

    @GetMapping("/{id}/url")
    public Result<String> getUrl(@PathVariable("id") Long id) {
        String url = fileService.getPresignedUrl(id);
        return Result.success(url);
    }

    @GetMapping("/{id}/info")
    public Result<MinioFile> getInfo(@PathVariable("id") Long id) {
        MinioFile minioFile = fileService.getById(id);
        return Result.success(minioFile);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        fileService.deleteFile(id);
        return Result.success();
    }

    /**
     * 流式代理文件内容（用于 PDF 预览、下载等）
     * 文件从 MinIO 读取 → 后端流式传输 → 浏览器，MinIO 不暴露公网
     */
    @GetMapping("/{id}/stream")
    public void streamFile(
            @PathVariable("id") Long id,
            @RequestParam(value = "download", required = false) String download,
            HttpServletResponse response) {
        // 需要登录才能访问文件
        StpUtil.checkLogin();
        MinioFile minioFile = fileService.getById(id);
        try (InputStream is = fileService.getFileStream(id);
             OutputStream os = response.getOutputStream()) {

            response.setContentType(minioFile.getMimeType() != null ? minioFile.getMimeType() : "application/octet-stream");

            if ("true".equalsIgnoreCase(download)) {
                String encodedName = URLEncoder.encode(minioFile.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
            } else {
                response.setHeader("Content-Disposition", "inline; filename=\"" + 
                    URLEncoder.encode(minioFile.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20") + "\"");
            }

            response.setContentLengthLong(minioFile.getFileSize());
            StreamUtils.copy(is, os);
            os.flush();
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.setStatus(500);
            }
        }
    }
}