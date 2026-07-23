package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "LoanReminderRequest", description = "登记催收记录请求")
public record LoanReminderInDTO(
        @Schema(description = "催收说明", example = "微信提醒了一次")
        @Size(max = 200, message = "催收说明长度不能超过200个字符")
        String note
) {
}
