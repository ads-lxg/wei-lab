package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量移动文献DTO
 */
@Data
@Schema(description = "批量移动文献请求")
public class DocumentBatchMoveDTO {

    @Schema(description = "文献ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "文献ID列表不能为空")
    private List<Long> documentIds;

    @Schema(description = "目标目录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标目录ID不能为空")
    private Long targetFolderId;
}
