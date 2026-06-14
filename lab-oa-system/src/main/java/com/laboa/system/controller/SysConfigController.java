package com.laboa.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.entity.SysConfig;
import com.laboa.common.mapper.SysConfigMapper;
import com.laboa.common.result.Result;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置控制器 - 管理网站名称、图标等
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigMapper sysConfigMapper;
    private final FileService fileService;
    private final MinioClient minioClient;

    /**
     * 获取所有配置（公开接口，登录即可访问）
     */
    @GetMapping("/api/config/list")
    public Result<Map<String, String>> listConfigs() {
        List<SysConfig> configs = sysConfigMapper.selectList(null);
        Map<String, String> map = configs.stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, c -> c.getConfigValue() != null ? c.getConfigValue() : ""));
        return Result.success(map);
    }

    /**
     * 获取单个配置值（公开接口）
     */
    @GetMapping("/api/config/{key}")
    public Result<String> getConfig(@PathVariable("key") String key) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key)
        );
        return Result.success(config != null ? config.getConfigValue() : null);
    }

    /**
     * 获取网站图标（直接返回图片二进制流，永不过期）
     * 图标存储在 MinIO，MySQL 中只存 fileId，避免 Base64 超长问题
     * 前端可直接用作 <img src="/api/config/icon">
     */
    @GetMapping("/api/config/icon")
    public ResponseEntity<byte[]> getSiteIcon() {
        // 1. 从 MySQL 获取 site_icon_file_id
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, "site_icon_file_id")
        );
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isEmpty()) {
            // 兼容旧数据：尝试从 site_icon_base64 读取
            return getSiteIconFromBase64();
        }

        try {
            Long fileId = Long.valueOf(config.getConfigValue());
            MinioFile minioFile = fileService.getById(fileId);
            if (minioFile == null) {
                return ResponseEntity.notFound().build();
            }

            // 从 MinIO 读取文件流
            try (var stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioFile.getBucket())
                    .object(minioFile.getStoredName())
                    .build())) {
                byte[] imageBytes = stream.readAllBytes();
                String contentType = minioFile.getMimeType() != null ? minioFile.getMimeType() : "image/png";
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(imageBytes);
            }
        } catch (Exception e) {
            log.error("从MinIO读取网站图标失败", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 兼容旧数据：从 site_icon_base64 读取图标
     */
    private ResponseEntity<byte[]> getSiteIconFromBase64() {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, "site_icon_base64")
        );
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            String base64Data = config.getConfigValue();
            String[] parts = base64Data.split(",", 2);
            if (parts.length < 2) {
                return ResponseEntity.notFound().build();
            }
            String mimeType = "image/png";
            if (parts[0].contains("image/")) {
                mimeType = parts[0].substring(parts[0].indexOf("image/"), parts[0].indexOf(";"));
            }
            byte[] imageBytes = java.util.Base64.getDecoder().decode(parts[1]);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(imageBytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 批量更新配置（仅管理员）
     * site_icon_base64 字段：前端传入 Base64 data URL，后端解码后上传到 MinIO，
     * MySQL 中只存 site_icon_file_id（MinIO 文件ID），不再存 Base64
     */
    @SaCheckPermission("system:config")
    @PostMapping("/api/config/update")
    public Result<Void> updateConfigs(@RequestBody Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // 特殊处理 site_icon_base64：上传到 MinIO，存 fileId
            if ("site_icon_base64".equals(key) && value != null && !value.isEmpty()) {
                try {
                    // 解析 Base64 data URL
                    String[] parts = value.split(",", 2);
                    if (parts.length < 2) continue;

                    String mimeType = "image/png";
                    if (parts[0].contains("image/")) {
                        mimeType = parts[0].substring(parts[0].indexOf("image/"), parts[0].indexOf(";"));
                    }
                    String ext = mimeType.substring(mimeType.indexOf("/") + 1);
                    byte[] imageBytes = java.util.Base64.getDecoder().decode(parts[1]);

                    // 上传到 MinIO
                    MinioFile minioFile = fileService.uploadBytes(
                            "site-icon." + ext, imageBytes, mimeType, 0L
                    );

                    // 删除旧的 MinIO 图标文件
                    SysConfig oldFileIdConfig = sysConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, "site_icon_file_id")
                    );
                    if (oldFileIdConfig != null && oldFileIdConfig.getConfigValue() != null) {
                        try {
                            Long oldFileId = Long.valueOf(oldFileIdConfig.getConfigValue());
                            fileService.deleteFile(oldFileId);
                        } catch (Exception e) {
                            log.warn("删除旧图标文件失败", e);
                        }
                    }

                    // 存 site_icon_file_id 到 MySQL
                    upsertConfig("site_icon_file_id", String.valueOf(minioFile.getId()));

                    // 清理旧的 site_icon_base64 数据（释放空间）
                    upsertConfig("site_icon_base64", "");

                    log.info("网站图标已上传到MinIO: fileId={}", minioFile.getId());
                } catch (Exception e) {
                    log.error("网站图标上传MinIO失败", e);
                }
                continue;
            }

            // 其他配置正常存储
            upsertConfig(key, value);
        }
        return Result.success();
    }

    private void upsertConfig(String key, String value) {
        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key)
        );
        if (existing != null) {
            existing.setConfigValue(value);
            sysConfigMapper.updateById(existing);
        } else {
            SysConfig newConfig = new SysConfig();
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            sysConfigMapper.insert(newConfig);
        }
    }
}
