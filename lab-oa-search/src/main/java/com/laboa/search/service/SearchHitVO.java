package com.laboa.search.service;

import lombok.Data;

@Data
public class SearchHitVO {

    private String docId;
    private String title;
    private String highlight;
    private String type;
    private Double score;
}