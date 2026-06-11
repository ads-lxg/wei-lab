package com.laboa.system.dto;

import lombok.Data;

@Data
public class UserPageDTO {

    private Integer page = 1;

    private Integer size = 10;

    private String keyword;

    private Integer status;
}