package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoanAccount", description = "借出可选账户")
public record LoanAccountOutDTO(
        @Schema(description = "账户ID", example = "1") Long id,
        @Schema(description = "账户名称", example = "现金账户") String name
) {
}
