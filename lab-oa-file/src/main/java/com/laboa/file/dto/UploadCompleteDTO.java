package com.laboa.file.dto;

import lombok.Data;

@Data
public class UploadCompleteDTO {

    private String fileMd5;

    private String fileName;
}