package com.laboa.literature.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("download_log")
public class DownloadLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long literatureId;

    private LocalDateTime downloadTime;
}