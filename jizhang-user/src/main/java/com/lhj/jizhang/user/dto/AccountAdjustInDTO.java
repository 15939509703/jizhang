package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountAdjustInDTO(
        @NotBlank(message = "请求ID不能为空")
        @Size(max = 64, message = "请求ID长度不能超过64个字符") String requestId,
        @NotNull(message = "目标余额不能为空")
        @Digits(integer = 17, fraction = 2, message = "目标余额最多保留2位小数") BigDecimal targetBalance,
        @Size(max = 1000, message = "备注长度不能超过1000个字符") String note
) {
}
