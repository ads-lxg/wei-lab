package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动文献DTO
 */
@Data
@Schema(description = "移动文献请求")
public class DocumentMoveDTO {

    @Schema(description = "文献ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文献ID不能为空")
    private Long documentId;

    @Schema(description = "目标目录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标目录ID不能为空")
    private Long targetFolderId;
}
