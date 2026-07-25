package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record RecurringConfirmInDTO(
        @DecimalMin(value = "0.01", message = "金额必须大于0")
        @Digits(integer = 17, fraction = 2, message = "金额最多保留2位小数") BigDecimal amount,
        Long categoryId,
        Long accountId,
        Long targetAccountId,
        Instant happenedAt,
        @Size(max = 128, message = "标题长度不能超过128个字符") String title,
        @Size(max = 1000, message = "备注长度不能超过1000个字符") String note
) {
}
