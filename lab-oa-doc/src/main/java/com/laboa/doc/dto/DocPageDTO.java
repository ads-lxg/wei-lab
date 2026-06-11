package com.laboa.doc.dto;

import lombok.Data;

@Data
public class DocPageDTO {

    private Integer page = 1;

    private Integer size = 10;

    private String keyword;

    private Integer status;
}