package com.laboa.file.service;

import com.laboa.file.entity.MinioFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileService {

    MinioFile uploadFile(MultipartFile file, Long uploaderId);

    /**
     * 从字节数组上传文件
     */
    MinioFile uploadBytes(String fileName, byte[] content, String contentType, Long uploaderId);

    String getPresignedUrl(Long fileId);

    String getPresignedUrl(Long fileId, Integer expiryMinutes);

    String extractText(InputStream inputStream);

    /**
     * 从字节数组解析文本，根据文件类型自动选择最优解析器
     * txt/md 等纯文本走快速路径，pdf/docx 走 Tika
     */
    String extractText(byte[] bytes, String fileName);

    MinioFile getById(Long fileId);

    void deleteFile(Long fileId);

    /**
     * 获取文件的文本内容（适用于文本类文件如md/txt）
     */
    String getFileContent(Long fileId);

    /**
     * 获取文件输入流（用于流式代理下载/预览）
     */
    InputStream getFileStream(Long fileId);
}