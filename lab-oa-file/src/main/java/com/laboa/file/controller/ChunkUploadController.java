package com.laboa.file.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.laboa.common.result.Result;
import com.laboa.file.dto.ChunkInitDTO;
import com.laboa.file.dto.UploadCompleteDTO;
import com.laboa.file.service.ChunkUploadService;
import com.laboa.file.vo.UploadCompleteVO;
import com.laboa.file.vo.UploadInitVO;
import com.laboa.file.vo.UploadProgressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@SaCheckPermission("literature:upload")
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    /**
     * 初始化分片上传
     */
    @PostMapping("/init")
    public Result<UploadInitVO> init(@RequestBody ChunkInitDTO dto) {
        log.info("收到上传初始化请求: fileMd5={}, fileName={}, fileSize={}, totalChunks={}",
                dto.getFileMd5(), dto.getFileName(), dto.getFileSize(), dto.getTotalChunks());
        UploadInitVO result = chunkUploadService.initUpload(dto);
        log.info("上传初始化完成: fileMd5={}, status={}", result.getFileMd5(), result.getStatus());
        return Result.success(result);
    }

    /**
     * 分片上传（并发安全）
     */
    @PostMapping("/chunk")
    public Result<String> chunk(@RequestParam("fileMd5") String fileMd5,
                                 @RequestParam("chunkIndex") int chunkIndex,
                                 @RequestParam("chunkData") MultipartFile chunkData) throws IOException {
        log.debug("收到分片: fileMd5={}, chunkIndex={}, size={}", fileMd5, chunkIndex, chunkData.getSize());
        chunkUploadService.uploadChunk(fileMd5, chunkIndex, chunkData.getBytes());
        return Result.success("ok");
    }

    /**
     * 查询上传进度（断点续传核心）
     */
    @GetMapping("/progress")
    public Result<UploadProgressVO> progress(@RequestParam("fileMd5") String fileMd5) {
        log.debug("查询进度: fileMd5={}", fileMd5);
        UploadProgressVO progress = chunkUploadService.getProgress(fileMd5);
        return Result.success(progress);
    }

    /**
     * 合并分片
     */
    @PostMapping("/complete")
    public Result<UploadCompleteVO> complete(@RequestBody UploadCompleteDTO dto) {
        log.info("收到合并请求: fileMd5={}, fileName={}", dto.getFileMd5(), dto.getFileName());
        UploadCompleteVO result = chunkUploadService.complete(dto);
        log.info("合并完成: fileMd5={}, fileId={}, minioPath={}", dto.getFileMd5(), result.getFileId(), result.getMinioPath());
        return Result.success(result);
    }
}