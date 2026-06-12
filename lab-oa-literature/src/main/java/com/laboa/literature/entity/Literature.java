package com.laboa.literature.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("literature")
public class Literature {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private String authors;

    @TableField("abstract")
    private String abstractText;

    private String keywords;

    /** 发表日期 */
    private LocalDate publishDate;

    /** 来源期刊 */
    private String sourceJournal;

    private String doi;

    private Long fileId;

    /** 文件类型: pdf / docx / md / txt 等 */
    private String fileType;

    private Long uploaderId;

    private Integer permissionLevel;

    /** 是否作为RAG来源: 0=否, 1=是 */
    private Integer ragSource;

    /** 解析状态: NONE / PENDING / SUCCESS / FAILED */
    private String parseStatus;

    private Integer viewCount;

    private Integer downloadCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}