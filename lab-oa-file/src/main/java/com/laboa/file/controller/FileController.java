package com.laboa.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.result.Result;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
}