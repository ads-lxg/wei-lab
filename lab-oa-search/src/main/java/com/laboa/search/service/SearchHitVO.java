package com.laboa.search.service;

import lombok.Data;

@Data
public class SearchHitVO {

    private String docId;
    private String title;
    private String fileName;
    private String content;
    private String docType;
    private Double score;
}