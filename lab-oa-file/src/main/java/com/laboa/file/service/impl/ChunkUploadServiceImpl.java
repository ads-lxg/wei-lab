package com.laboa.file.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.exception.BusinessException;
import com.laboa.file.config.MinioConfig;
import com.laboa.file.dto.ChunkInitDTO;
import com.laboa.file.dto.UploadCompleteDTO;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.mapper.MinioFileMapper;
import com.laboa.file.service.ChunkUploadService;
import com.laboa.file.service.FileService;
import com.laboa.file.vo.UploadCompleteVO;
import com.laboa.file.vo.UploadInitVO;
import com.laboa.file.vo.UploadProgressVO;
import io.minio.*;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private static final String REDIS_KEY_UPLOAD = "upload:";
    private static final String REDIS_KEY_CHUNKS = "chunks:";
    private static final String STATUS_UPLOADING = "uploading";
    private static final String STATUS_COMPLETED = "completed";
    private static final int COMPOSE_LIMIT = 1000;

    private final MinioConfig minioConfig;
    private final MinioClient minioClient;
    private final MinioFileMapper minioFileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileService fileService;

    @Value("${upload.temp-dir:#{systemProperties['java.io.tmpdir'] + '/uploads'}}")
    private String tempDir;

    // ==================== 初始化 ====================

    @Override
    public UploadInitVO initUpload(ChunkInitDTO dto) {
        String fileMd5 = dto.getFileMd5();
        if (StrUtil.isBlank(fileMd5)) {
            throw new BusinessException("fileMd5 不能为空");
        }

        // 检查 MySQL 是否有已完成文件（秒传）
        MinioFile existing = minioFileMapper.selectOne(
                new LambdaQueryWrapper<MinioFile>()
                        .eq(MinioFile::getMd5, fileMd5)
                        .eq(MinioFile::getStatus, 1)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            log.info("秒传命中: fileMd5={}, fileId={}", fileMd5, existing.getId());
            return UploadInitVO.builder()
                    .uploadId(fileMd5)
                    .fileMd5(fileMd5)
                    .status("skip")
                    .fileId(existing.getId())
                    .build();
        }

        // Redis 幂等：已存在上传元数据则直接返回
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(REDIS_KEY_UPLOAD + fileMd5))) {
            log.info("上传已初始化，跳过: fileMd5={}", fileMd5);
            return UploadInitVO.builder()
                    .uploadId(fileMd5)
                    .fileMd5(fileMd5)
                    .status("ready")
                    .build();
        }

        // 本地文件路径
        File dir = new File(tempDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("创建临时目录失败: " + tempDir);
        }
        Path localPath = new File(dir, fileMd5 + ".part").toPath();

        // 预分配本地文件（检查磁盘空间）
        try {
            try (FileChannel channel = FileChannel.open(localPath,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.SPARSE)) {
                // 预分配文件大小（sparse 文件不占用实际磁盘空间）
                channel.position(dto.getFileSize() - 1);
                channel.write(ByteBuffer.wrap(new byte[1]));
            }
            log.info("本地临时文件预分配成功: {}, size={}", localPath, dto.getFileSize());
        } catch (Exception e) {
            log.error("预分配本地文件失败: {}", localPath, e);
            throw new BusinessException("磁盘空间不足或创建文件失败: " + e.getMessage());
        }

        // Redis 元数据哈希
        Map<String, String> meta = new HashMap<>();
        meta.put("fileName", dto.getFileName());
        meta.put("fileSize", String.valueOf(dto.getFileSize()));
        meta.put("chunkSize", String.valueOf(dto.getChunkSize()));
        meta.put("totalChunks", String.valueOf(dto.getTotalChunks()));
        meta.put("localFilePath", localPath.toString());
        meta.put("status", STATUS_UPLOADING);
        stringRedisTemplate.opsForHash().putAll(REDIS_KEY_UPLOAD + fileMd5, meta);

        // 初始化 Bitmap（固定长度）
        stringRedisTemplate.opsForValue().setBit(REDIS_KEY_CHUNKS + fileMd5,
                dto.getTotalChunks() - 1, false);

        log.info("上传初始化完成: fileMd5={}, fileName={}, totalChunks={}", fileMd5, dto.getFileName(), dto.getTotalChunks());

        return UploadInitVO.builder()
                .uploadId(fileMd5)
                .fileMd5(fileMd5)
                .status("ready")
                .build();
    }

    // ==================== 分片上传 ====================

    @Override
    public void uploadChunk(String fileMd5, int chunkIndex, byte[] chunkData) {
        if (chunkIndex < 1) {
            throw new BusinessException("chunkIndex 必须 >= 1");
        }

        // 校验元数据状态
        String status = (String) stringRedisTemplate.opsForHash()
                .get(REDIS_KEY_UPLOAD + fileMd5, "status");
        if (!STATUS_UPLOADING.equals(status)) {
            throw new BusinessException("上传状态异常: " + status);
        }

        String totalChunksStr = (String) stringRedisTemplate.opsForHash()
                .get(REDIS_KEY_UPLOAD + fileMd5, "totalChunks");
        int totalChunks = Integer.parseInt(totalChunksStr);
        if (chunkIndex > totalChunks) {
            throw new BusinessException("chunkIndex 超出范围: " + chunkIndex + "/" + totalChunks);
        }

        String localFilePath = (String) stringRedisTemplate.opsForHash()
                .get(REDIS_KEY_UPLOAD + fileMd5, "localFilePath");
        String chunkSizeStr = (String) stringRedisTemplate.opsForHash()
                .get(REDIS_KEY_UPLOAD + fileMd5, "chunkSize");
        long chunkSize = Long.parseLong(chunkSizeStr);

        // ---- 步骤1: 写入 MinIO 临时对象 ----
        String tmpObjectName = String.format("tmp/%s/chunk_%d", fileMd5, chunkIndex);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(tmpObjectName)
                    .stream(new ByteArrayInputStream(chunkData), chunkData.length, -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception e) {
            log.error("MinIO写入分片失败: fileMd5={}, chunk={}", fileMd5, chunkIndex, e);
            throw new BusinessException("MinIO 写入失败: " + e.getMessage());
        }

        // ---- 步骤2: Redis Bitmap 标记 ----
        stringRedisTemplate.opsForValue().setBit(REDIS_KEY_CHUNKS + fileMd5, chunkIndex - 1, true);

        // ---- 步骤3: 本地文件写入（精确偏移量） ----
        long offset = (long) (chunkIndex - 1) * chunkSize;
        try (FileChannel channel = FileChannel.open(Path.of(localFilePath),
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(offset);
            channel.write(ByteBuffer.wrap(chunkData));
        } catch (Exception e) {
            log.error("本地文件写入失败: fileMd5={}, chunk={}, offset={}", fileMd5, chunkIndex, offset, e);
            throw new BusinessException("本地文件写入失败: " + e.getMessage());
        }

        log.debug("分片上传完成: fileMd5={}, chunkIndex={}, size={}", fileMd5, chunkIndex, chunkData.length);
    }

    // ==================== 进度查询 ====================

    @Override
    public UploadProgressVO getProgress(String fileMd5) {
        String uploadKey = REDIS_KEY_UPLOAD + fileMd5;

        // 检查 Redis 元数据
        Map<Object, Object> meta = stringRedisTemplate.opsForHash().entries(uploadKey);
        if (meta.isEmpty()) {
            throw new BusinessException("未找到上传记录: " + fileMd5);
        }

        String status = (String) meta.get("status");
        int totalChunks = Integer.parseInt((String) meta.get("totalChunks"));
        long chunkSize = Long.parseLong((String) meta.get("chunkSize"));
        long fileSize = Long.parseLong((String) meta.get("fileSize"));

        // 计算 Bitmap 中 1 的数量
        Long uploadedCount = stringRedisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<Long>) connection ->
                        connection.bitCount((REDIS_KEY_CHUNKS + fileMd5).getBytes()));
        int uploaded = uploadedCount != null ? uploadedCount.intValue() : 0;

        // 构造已上传和缺失索引列表
        List<Integer> uploadedIndexes = new ArrayList<>();
        List<Integer> missingIndexes = new ArrayList<>();
        String chunksKey = REDIS_KEY_CHUNKS + fileMd5;
        for (int i = 0; i < totalChunks; i++) {
            Boolean bit = stringRedisTemplate.opsForValue().getBit(chunksKey, i);
            if (Boolean.TRUE.equals(bit)) {
                uploadedIndexes.add(i + 1);
            } else {
                missingIndexes.add(i + 1);
            }
        }

        // 已完成的秒传
        if (STATUS_COMPLETED.equals(status)) {
            String fileIdStr = (String) meta.get("fileId");
            Long fileId = fileIdStr != null ? Long.parseLong(fileIdStr) : null;
            return UploadProgressVO.builder()
                    .totalChunks(totalChunks)
                    .chunkSize(chunkSize)
                    .fileSize(fileSize)
                    .uploadedCount(totalChunks)
                    .status(STATUS_COMPLETED)
                    .fileId(fileId)
                    .uploadedIndexes(uploadedIndexes)
                    .missingIndexes(List.of())
                    .build();
        }

        return UploadProgressVO.builder()
                .totalChunks(totalChunks)
                .chunkSize(chunkSize)
                .fileSize(fileSize)
                .uploadedCount(uploaded)
                .status(STATUS_UPLOADING)
                .uploadedIndexes(uploadedIndexes)
                .missingIndexes(missingIndexes)
                .build();
    }

    // ==================== 合并触发 ====================

    @Override
    public UploadCompleteVO complete(UploadCompleteDTO dto) {
        String fileMd5 = dto.getFileMd5();
        String uploadKey = REDIS_KEY_UPLOAD + fileMd5;
        String chunksKey = REDIS_KEY_CHUNKS + fileMd5;
        String fileName = dto.getFileName();

        // 1. 获取元数据
        Map<Object, Object> meta = stringRedisTemplate.opsForHash().entries(uploadKey);
        if (meta.isEmpty()) {
            throw new BusinessException("未找到上传记录: " + fileMd5);
        }

        String status = (String) meta.get("status");
        if (STATUS_COMPLETED.equals(status)) {
            // 已合并，幂等返回
            String fileIdStr = (String) meta.get("fileId");
            Long fileId = Long.parseLong(fileIdStr);
            MinioFile minioFile = minioFileMapper.selectById(fileId);
            return toVO(minioFile);
        }

        int totalChunks = Integer.parseInt((String) meta.get("totalChunks"));
        long fileSize = Long.parseLong((String) meta.get("fileSize"));
        String localFilePath = (String) meta.get("localFilePath");

        if (fileName == null) {
            fileName = (String) meta.get("fileName");
        }

        // 2. 完整性校验
        Long uploadedCount = stringRedisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<Long>) connection ->
                        connection.bitCount(chunksKey.getBytes()));
        if (uploadedCount == null || uploadedCount.intValue() != totalChunks) {
            throw new BusinessException("分片未全部上传: " + (uploadedCount != null ? uploadedCount : 0) + "/" + totalChunks);
        }

        try {
            // 校验本地文件大小
            Path localPath = Path.of(localFilePath);
            if (!Files.exists(localPath) || Files.size(localPath) != fileSize) {
                log.warn("本地文件缺失或大小不符，从 MinIO 重建: fileMd5={}", fileMd5);
                rebuildLocalFile(fileMd5, localPath, totalChunks, fileSize);
            }

            // 3. MinIO ComposeObject
            String extension = "";
            if (fileName != null && fileName.contains(".")) {
                extension = fileName.substring(fileName.lastIndexOf("."));
            }
            String storedName = UUID.randomUUID() + extension;

            composeParts(fileMd5, totalChunks, storedName);

            // 4. 创建 MinioFile 记录
            MinioFile minioFile = new MinioFile();
            minioFile.setOriginalName(fileName);
            minioFile.setStoredName(storedName);
            minioFile.setBucket(minioConfig.getBucket());
            minioFile.setFilePath(storedName);
            minioFile.setFileSize(fileSize);
            minioFile.setMimeType(getMimeType(extension));
            minioFile.setMd5(fileMd5);
            minioFile.setUploaderId(0L);
            minioFile.setStatus(1);
            minioFile.setCreateTime(LocalDateTime.now());
            minioFile.setDeleted(0);
            minioFileMapper.insert(minioFile);

            // 5. 更新 Redis 状态
            Map<String, String> updateMap = new HashMap<>();
            updateMap.put("status", STATUS_COMPLETED);
            updateMap.put("fileId", String.valueOf(minioFile.getId()));
            stringRedisTemplate.opsForHash().putAll(uploadKey, updateMap);

            log.info("文件合并完成: fileMd5={}, fileId={}, storedName={}", fileMd5, minioFile.getId(), storedName);

            // 6. 解析正文（使用本地文件，免回下载）
            long parseStart = System.currentTimeMillis();
            String parsedText = null;
            int textLength = 0;
            try {
                byte[] fileBytes = Files.readAllBytes(localPath);
                parsedText = fileService.extractText(fileBytes, fileName);
                if (parsedText != null) {
                    textLength = parsedText.length();
                }
            } catch (Exception e) {
                log.error("文件解析失败: fileMd5={}, fileName={}", fileMd5, fileName, e);
            }
            long parseTime = System.currentTimeMillis() - parseStart;
            log.info("文件解析完成: fileMd5={}, textLength={}, parseTime={}ms", fileMd5, textLength, parseTime);

            // 7. 清理临时资源（异步）
            asyncCleanup(fileMd5, totalChunks, localPath);

            String previewText = (parsedText != null && parsedText.length() > 2000)
                    ? parsedText.substring(0, 2000) + "\n\n... (全文共 " + textLength + " 字符，此处仅显示前 2000 字符)"
                    : parsedText;

            return UploadCompleteVO.builder()
                    .fileId(minioFile.getId())
                    .fileName(fileName)
                    .minioPath(storedName)
                    .fileSize(fileSize)
                    .status("completed")
                    .parsedText(previewText)
                    .textLength(textLength)
                    .parseTimeMs(parseTime)
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件合并失败: fileMd5={}", fileMd5, e);
            throw new BusinessException("文件合并失败: " + e.getMessage());
        }
    }

    // ==================== ComposeObject ====================

    private void composeParts(String fileMd5, int totalChunks, String storedName) throws Exception {
        List<ComposeSource> sources = new ArrayList<>();

        for (int i = 1; i <= totalChunks; i++) {
            sources.add(ComposeSource.builder()
                    .bucket(minioConfig.getBucket())
                    .object(String.format("tmp/%s/chunk_%d", fileMd5, i))
                    .build());
        }

        // MinIO ComposeObject 单次上限 1000 个源，超过则分组 compose
        if (sources.size() <= COMPOSE_LIMIT) {
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(storedName)
                    .sources(sources)
                    .build());
        } else {
            // 分组 compose，先用中间对象合并，再最终合并
            List<String> intermediate = new ArrayList<>();
            for (int i = 0; i < sources.size(); i += COMPOSE_LIMIT) {
                int end = Math.min(i + COMPOSE_LIMIT, sources.size());
                String interKey = String.format("tmp/%s/_inter_%d", fileMd5, i / COMPOSE_LIMIT);
                minioClient.composeObject(ComposeObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(interKey)
                        .sources(sources.subList(i, end))
                        .build());
                intermediate.add(interKey);
            }
            List<ComposeSource> interSources = intermediate.stream()
                    .map(key -> ComposeSource.builder()
                            .bucket(minioConfig.getBucket())
                            .object(key)
                            .build())
                    .collect(Collectors.toList());
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(storedName)
                    .sources(interSources)
                    .build());

            // 清理中间对象
            for (String interKey : intermediate) {
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(interKey)
                            .build());
                } catch (Exception ignored) {
                }
            }
        }

        log.info("ComposeObject完成: {} 分片 → {}", totalChunks, storedName);
    }

    // ==================== 本地文件重建 ====================

    private void rebuildLocalFile(String fileMd5, Path localPath, int totalChunks, long fileSize) throws Exception {
        // 删除旧文件
        Files.deleteIfExists(localPath);
        // 创建新的 sparse 文件
        try (FileChannel channel = FileChannel.open(localPath,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.SPARSE)) {
            channel.position(fileSize - 1);
            channel.write(ByteBuffer.wrap(new byte[1]));
        }

        // 从 MinIO 下载每个分片并写入
        String chunkSizeStr = (String) stringRedisTemplate.opsForHash()
                .get(REDIS_KEY_UPLOAD + fileMd5, "chunkSize");
        long chunkSize = Long.parseLong(chunkSizeStr);

        for (int i = 1; i <= totalChunks; i++) {
            byte[] data;
            try (var is = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(String.format("tmp/%s/chunk_%d", fileMd5, i))
                    .build())) {
                data = is.readAllBytes();
            }
            long offset = (long) (i - 1) * chunkSize;
            try (FileChannel channel = FileChannel.open(localPath,
                    java.nio.file.StandardOpenOption.WRITE)) {
                channel.position(offset);
                channel.write(ByteBuffer.wrap(data));
            }
        }
        log.info("本地文件从 MinIO 重建成功: fileMd5={}", fileMd5);
    }

    // ==================== 异步清理 ====================

    private void asyncCleanup(String fileMd5, int totalChunks, Path localPath) {
        new Thread(() -> {
            try {
                // 删除本地临时文件
                Files.deleteIfExists(localPath);
                log.info("本地临时文件已清理: {}", localPath);

                // 删除 MinIO tmp 分片对象（最多 1000 个一批）
                List<DeleteObject> toDelete = new ArrayList<>();
                for (int i = 1; i <= totalChunks; i++) {
                    toDelete.add(new DeleteObject(String.format("tmp/%s/chunk_%d", fileMd5, i)));
                }
                // 分批删除
                for (int i = 0; i < toDelete.size(); i += 1000) {
                    int end = Math.min(i + 1000, toDelete.size());
                    minioClient.removeObjects(RemoveObjectsArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .objects(toDelete.subList(i, end))
                            .build());
                }

                // 删除中间对象
                var iter = minioClient.listObjects(ListObjectsArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .prefix("tmp/" + fileMd5 + "/_inter_")
                        .build());
                for (var r : iter) {
                    try {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(minioConfig.getBucket())
                                .object(r.get().objectName())
                                .build());
                    } catch (Exception ignored) {
                    }
                }

                // 清理 Redis 键
                stringRedisTemplate.delete(REDIS_KEY_UPLOAD + fileMd5);
                stringRedisTemplate.delete(REDIS_KEY_CHUNKS + fileMd5);

                log.info("临时资源清理完成: fileMd5={}", fileMd5);
            } catch (Exception e) {
                log.error("清理临时资源失败: fileMd5={}", fileMd5, e);
            }
        }, "cleanup-" + fileMd5.substring(0, 8)).start();
    }

    // ==================== 工具方法 ====================

    private UploadCompleteVO toVO(MinioFile f) {
        return UploadCompleteVO.builder()
                .fileId(f.getId())
                .fileName(f.getOriginalName())
                .minioPath(f.getStoredName())
                .fileSize(f.getFileSize())
                .status("completed")
                .build();
    }

    private String getMimeType(String extension) {
        return switch (extension.toLowerCase()) {
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".ppt" -> "application/vnd.ms-powerpoint";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".txt" -> "text/plain";
            case ".md" -> "text/markdown";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}