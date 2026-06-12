package com.laboa.literature.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LiteraturePageDTO {

    private Integer page = 1;

    private Integer size = 10;

    private String keyword;

    private String author;

    /** 发表日期筛选（精确匹配） */
    private LocalDate publishDate;

    /** 来源期刊筛选（模糊匹配） */
    private String sourceJournal;
}