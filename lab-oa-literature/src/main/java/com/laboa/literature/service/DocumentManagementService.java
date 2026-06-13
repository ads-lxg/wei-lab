package com.laboa.literature.service;

import com.laboa.common.result.PageResult;
import com.laboa.literature.dto.*;
import com.laboa.literature.vo.BatchUploadResultVO;
import com.laboa.literature.vo.LiteratureDetailVO;
import com.laboa.literature.vo.LiteratureListItemVO;
import com.laboa.literature.vo.LiteratureRecycleVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文献管理服务接口
 * <p>
 * 提供文献的完整生命周期管理：上传、查询、搜索、移动、删除、回收站、下载等
 */
public interface DocumentManagementService {

    /**
     * 上传文献（单个）
     * 调用已有文件上传组件，上传成功后创建文献记录并关联目录
     */
    LiteratureDetailVO upload(DocumentUploadDTO dto, MultipartFile file, Long uploaderId);

    /**
     * 批量上传文献（Excel+文件）
     * 解析Excel元数据，按文件名匹配文件，批量创建文献记录
     * RAG来源由Excel第8列指定（0=仅全文检索, 1=全文检索+分块向量化）
     */
    BatchUploadResultVO batchUpload(Long folderId, MultipartFile excelFile, List<MultipartFile> files, Long uploaderId);

    /**
     * 删除文献（逻辑删除，进入回收站）
     */
    void delete(Long documentId, Long operatorId);

    /**
     * 批量删除文献（逻辑删除，进入回收站）
     */
    void batchDelete(List<Long> documentIds, Long operatorId);

    /**
     * 移动文献到目标目录
     */
    void move(Long documentId, Long targetFolderId);

    /**
     * 批量移动文献到目标目录
     */
    void batchMove(List<Long> documentIds, Long targetFolderId);

    /**
     * 下载文献（返回预签名URL，并增加下载次数）
     */
    String download(Long documentId, Long userId);

    /**
     * 批量下载文献ID列表（用于ZIP打包下载）
     */
    List<Long> getDownloadFileIds(List<Long> documentIds, Long userId);

    /**
     * 查询目录下文献（分页）
     */
    PageResult<LiteratureListItemVO> listByFolder(DocumentFolderQueryDTO dto);

    /**
     * 文献详情
     */
    LiteratureDetailVO getDetail(Long documentId);

    /**
     * 文献搜索（全局模糊搜索，分页）
     */
    PageResult<LiteratureListItemVO> search(DocumentSearchDTO dto);

    /**
     * 查询回收站文献（分页）
     */
    PageResult<LiteratureRecycleVO> listRecycleBin(Integer page, Integer size);

    /**
     * 恢复文献（从回收站恢复）
     */
    void recover(Long documentId);

    /**
     * 批量恢复文献
     */
    void batchRecover(List<Long> documentIds);

    /**
     * 彻底删除文献（物理删除，删除数据库记录和物理文件）
     */
    void permanentDelete(Long documentId);

    /**
     * 批量彻底删除文献
     */
    void batchPermanentDelete(List<Long> documentIds);

    /**
     * 获取文献解析状态（用于前端轮询上传/解析进度）
     * @return 解析状态字符串: NONE / PENDING / SUCCESS / FAILED
     */
    String getParseStatus(Long documentId);

    /**
     * 批量获取文献解析状态
     * @param documentIds 文献ID列表
     * @return 文献ID->解析状态映射
     */
    Map<Long, String> batchGetParseStatus(List<Long> documentIds);
}
