package com.laboa.file.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("minio_file")
public class MinioFile {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String originalName;

    private String storedName;

    private String bucket;

    private String filePath;

    private Long fileSize;

    private String mimeType;

    private String md5;

    private Long uploaderId;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}