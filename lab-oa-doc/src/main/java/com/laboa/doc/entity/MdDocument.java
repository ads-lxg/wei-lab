package com.laboa.doc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("md_document")
public class MdDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private Long fileId;

    /** 文件类型: pdf / docx / md / txt 等 */
    private String fileType;

    private Long authorId;

    private Integer status;

    /** 是否作为RAG来源: 内部文档强制为 1 */
    private Integer ragSource;

    /** 解析状态: NONE / PENDING / SUCCESS / FAILED */
    private String parseStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}