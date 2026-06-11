package com.laboa.file.dto;

import lombok.Data;

@Data
public class ChunkInitDTO {

    private String fileMd5;

    private String fileName;

    private Long fileSize;

    private Long chunkSize;

    private Integer totalChunks;
}