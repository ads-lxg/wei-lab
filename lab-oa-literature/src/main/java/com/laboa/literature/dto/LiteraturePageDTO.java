package com.laboa.literature.dto;

import lombok.Data;

@Data
public class LiteraturePageDTO {

    private Integer page = 1;

    private Integer size = 10;

    private String keyword;

    private String author;

    private Integer publishYear;
}