package com.laboa.literature.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文献目录实体
 */
@Data
@TableName("rag_folder")
public class RagFolder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父目录ID，0表示根目录 */
    private Long parentId;

    /** 目录名称 */
    private String folderName;

    /** 目录路径，格式 /1/2/3 */
    private String path;

    /** 目录层级，从1开始 */
    private Integer levelNo;

    /** 排序号 */
    private Integer sortOrder;

    /** 创建人ID */
    private Long createUser;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0=正常，1=已删除 */
    @TableLogic
    private Integer deleted;

    /** 逻辑删除时间（定时清理任务根据此字段判断） */
    private LocalDateTime deletedTime;
}
