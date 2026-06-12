package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动目录请求DTO
 */
@Data
@Schema(description = "移动目录请求")
public class FolderMoveDTO {

    @Schema(description = "要移动的目录ID", example = "3")
    @NotNull(message = "目录ID不能为空")
    private Long folderId;

    @Schema(description = "目标父目录ID", example = "5")
    @NotNull(message = "目标父目录ID不能为空")
    private Long targetParentId;
}
