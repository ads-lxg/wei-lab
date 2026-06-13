package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 批量上传文献DTO（Excel+文件）
 */
@Data
@Schema(description = "批量上传文献请求")
public class DocumentBatchUploadDTO {

    @Schema(description = "所属目录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属目录ID不能为空")
    private Long folderId;
}
