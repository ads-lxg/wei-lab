package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改目录名称请求DTO
 */
@Data
@Schema(description = "修改目录名称请求")
public class FolderUpdateDTO {

    @Schema(description = "目录ID", example = "10")
    @NotNull(message = "目录ID不能为空")
    private Long id;

    @Schema(description = "新目录名称", example = "LLM论文")
    @NotBlank(message = "目录名称不能为空")
    private String folderName;
}
