package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建目录请求DTO
 */
@Data
@Schema(description = "创建目录请求")
public class FolderCreateDTO {

    @Schema(description = "父目录ID，0表示根目录", example = "1")
    @NotNull(message = "父目录ID不能为空")
    private Long parentId;

    @Schema(description = "目录名称", example = "Transformer")
    @NotBlank(message = "目录名称不能为空")
    private String folderName;
}
