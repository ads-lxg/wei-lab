package com.laboa.file.service;

import com.laboa.file.dto.ChunkInitDTO;
import com.laboa.file.dto.UploadCompleteDTO;
import com.laboa.file.vo.UploadCompleteVO;
import com.laboa.file.vo.UploadInitVO;
import com.laboa.file.vo.UploadProgressVO;

public interface ChunkUploadService {

    /** 初始化分片上传 */
    UploadInitVO initUpload(ChunkInitDTO dto);

    /** 上传单个分片 */
    void uploadChunk(String fileMd5, int chunkIndex, byte[] chunkData);

    /** 查询上传进度（断点续传） */
    UploadProgressVO getProgress(String fileMd5);

    /** 合并分片（前端所有分片发完后调用） */
    UploadCompleteVO complete(UploadCompleteDTO dto);
}