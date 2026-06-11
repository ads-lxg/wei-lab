package com.laboa.file.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitVO {

    /** 上传标识（等于 fileMd5） */
    private String uploadId;

    /** 文件MD5 */
    private String fileMd5;

    /** ready 正常初始化；skip 秒传跳过 */
    private String status;

    /** 秒传时已有的文件ID */
    private Long fileId;
}