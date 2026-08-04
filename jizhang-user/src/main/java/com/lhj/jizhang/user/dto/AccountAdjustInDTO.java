package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountAdjustInDTO(
        @NotBlank(message = "请求ID不能为空")
        @Size(max = 64, message = "请求ID长度不能超过64个字符") String requestId,
        @Schema(description = "目标余额；负债账户表示目标可用额度")
        @NotNull(message = "目标余额不能为空")
        @Digits(integer = 17, fraction = 2, message = "目标余额最多保留2位小数") BigDecimal targetBalance,
        @Size(max = 1000, message = "备注长度不能超过1000个字符") String note
) {
}
