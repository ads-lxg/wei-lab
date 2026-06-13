package com.laboa.literature.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除文献DTO
 */
@Data
@Schema(description = "批量删除文献请求")
public class DocumentBatchDeleteDTO {

    @Schema(description = "文献ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "文献ID列表不能为空")
    private List<Long> documentIds;
}
