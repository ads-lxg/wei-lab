package com.laboa.file.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.laboa.common.exception.BusinessException;
import com.laboa.file.config.MinioConfig;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.mapper.MinioFileMapper;
import com.laboa.file.service.FileService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final MinioFileMapper minioFileMapper;

    private final Tika tika = new Tika();

    @Override
    @Transactional
    public MinioFile uploadFile(MultipartFile file, Long uploaderId) {
        try {
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String storedName = UUID.randomUUID().toString() + extension;
            String mimeType = file.getContentType();
            byte[] bytes = file.getBytes();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(storedName)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(mimeType)
                    .build());

            String md5 = DigestUtil.md5Hex(bytes);

            MinioFile minioFile = new MinioFile();
            minioFile.setOriginalName(originalName);
            minioFile.setStoredName(storedName);
            minioFile.setBucket(minioConfig.getBucket());
            minioFile.setFilePath(storedName);
            minioFile.setFileSize((long) bytes.length);
            minioFile.setMimeType(mimeType);
            minioFile.setMd5(md5);
            minioFile.setUploaderId(uploaderId);
            minioFile.setStatus(1);
            minioFile.setDeleted(0);
            minioFileMapper.insert(minioFile);

            log.info("File uploaded: {} -> {}, size: {}", originalName, storedName, bytes.length);
            return minioFile;
        } catch (Exception e) {
            log.error("File upload failed", e);
            throw new BusinessException("文件上传失败");
        }
    }

    @Override
    @Transactional
    public MinioFile uploadBytes(String fileName, byte[] content, String contentType, Long uploaderId) {
        try {
            String extension = "";
            if (fileName != null && fileName.contains(".")) {
                extension = fileName.substring(fileName.lastIndexOf("."));
            }
            String storedName = UUID.randomUUID().toString() + extension;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(storedName)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());

            String md5 = DigestUtil.md5Hex(content);

            MinioFile minioFile = new MinioFile();
            minioFile.setOriginalName(fileName);
            minioFile.setStoredName(storedName);
            minioFile.setBucket(minioConfig.getBucket());
            minioFile.setFilePath(storedName);
            minioFile.setFileSize((long) content.length);
            minioFile.setMimeType(contentType);
            minioFile.setMd5(md5);
            minioFile.setUploaderId(uploaderId);
            minioFile.setStatus(1);
            minioFile.setDeleted(0);
            minioFileMapper.insert(minioFile);

            log.info("File uploaded from bytes: {} -> {}, size: {}", fileName, storedName, content.length);
            return minioFile;
        } catch (Exception e) {
            log.error("File upload from bytes failed", e);
            throw new BusinessException("文件上传失败");
        }
    }

    @Override
    public String getPresignedUrl(Long fileId) {
        return getPresignedUrl(fileId, 60);
    }

    @Override
    public String getPresignedUrl(Long fileId, Integer expiryMinutes) {
        MinioFile minioFile = getById(fileId);
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioFile.getBucket())
                    .object(minioFile.getStoredName())
                    .method(Method.GET)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for file: {}", fileId, e);
            throw new BusinessException("生成下载链接失败");
        }
    }

    @Override
    public String extractText(InputStream inputStream) {
        try {
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            log.error("Text extraction failed", e);
            throw new BusinessException("文本解析失败");
        }
    }

    private static final java.util.Set<String> FAST_PATH_EXTENSIONS = java.util.Set.of(
            "txt", "md", "markdown", "csv", "log", "json", "xml", "yaml", "yml", "properties", "html", "htm"
    );

    @Override
    public String extractText(byte[] bytes, String fileName) {
        String ext = getFileExtension(fileName);
        long start = System.currentTimeMillis();

        try {
            String text;

            // 第一层：纯文本文件 → 直接 UTF-8 解码（毫秒级）
            if (FAST_PATH_EXTENSIONS.contains(ext)) {
                text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                log.info("快速路径解析: ext={}, size={}, time={}ms", ext, bytes.length, System.currentTimeMillis() - start);
                return text;
            }

            // 第二层：PDF → PDFBox 直接提取（比 Tika 快 3-5 倍）
            if ("pdf".equals(ext)) {
                text = extractPdf(bytes);
                log.info("PDFBox解析: size={}, textLength={}, time={}ms", bytes.length, text.length(), System.currentTimeMillis() - start);
                return text;
            }

            // 第三层：DOCX → POI XWPF 直接提取
            if ("docx".equals(ext)) {
                text = extractDocx(bytes);
                log.info("POI-DOCX解析: size={}, textLength={}, time={}ms", bytes.length, text.length(), System.currentTimeMillis() - start);
                return text;
            }

            // 第四层：DOC → POI HWPF 直接提取
            if ("doc".equals(ext)) {
                text = extractDoc(bytes);
                log.info("POI-DOC解析: size={}, textLength={}, time={}ms", bytes.length, text.length(), System.currentTimeMillis() - start);
                return text;
            }

            // 第五层：其他格式 → Tika 兜底
            text = tika.parseToString(new ByteArrayInputStream(bytes));
            log.info("Tika兜底解析: ext={}, size={}, textLength={}, time={}ms", ext, bytes.length, text.length(), System.currentTimeMillis() - start);
            return text;

        } catch (Exception e) {
            log.error("文本解析失败: fileName={}, ext={}, error={}", fileName, ext, e.getMessage(), e);
            throw new BusinessException("文本解析失败: " + fileName);
        }
    }

    /**
     * PDFBox 直接提取 PDF 文本（跳过 Tika 的自动检测和元数据解析开销）
     */
    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 关闭排序以提高速度（对单栏文档足够准确）
            stripper.setSortByPosition(false);
            stripper.setStartPage(1);
            stripper.setEndPage(doc.getNumberOfPages());
            return stripper.getText(doc);
        }
    }

    /**
     * POI 直接提取 DOCX 文本
     */
    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    /**
     * POI 直接提取 DOC 文本
     */
    private String extractDoc(byte[] bytes) throws Exception {
        try (WordExtractor extractor = new WordExtractor(new ByteArrayInputStream(bytes))) {
            return extractor.getText();
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }

    @Override
    public MinioFile getById(Long fileId) {
        MinioFile minioFile = minioFileMapper.selectById(fileId);
        if (minioFile == null) {
            throw new BusinessException("文件不存在");
        }
        return minioFile;
    }

    @Override
    @Transactional
    public void deleteFile(Long fileId) {
        MinioFile minioFile = getById(fileId);
        minioFileMapper.deleteById(fileId);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioFile.getBucket())
                    .object(minioFile.getStoredName())
                    .build());
        } catch (Exception e) {
            log.error("Failed to remove file from MinIO: {}", minioFile.getStoredName(), e);
        }
        log.info("File deleted: {}", fileId);
    }

    @Override
    public String getFileContent(Long fileId) {
        MinioFile minioFile = getById(fileId);
        try (InputStream is = minioClient.getObject(io.minio.GetObjectArgs.builder()
                .bucket(minioFile.getBucket())
                .object(minioFile.getStoredName())
                .build())) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to read file content: {}", fileId, e);
            throw new BusinessException("读取文件内容失败");
        }
    }

    @Override
    public InputStream getFileStream(Long fileId) {
        MinioFile minioFile = getById(fileId);
        try {
            return minioClient.getObject(io.minio.GetObjectArgs.builder()
                    .bucket(minioFile.getBucket())
                    .object(minioFile.getStoredName())
                    .build());
        } catch (Exception e) {
            log.error("Failed to get file stream: {}", fileId, e);
            throw new BusinessException("读取文件失败");
        }
    }
}