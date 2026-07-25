package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record RecurringRuleUpdateInDTO(
        @NotBlank(message = "账单类型不能为空")
        @Pattern(regexp = "EXPENSE|INCOME|TRANSFER", message = "账单类型不正确") String transactionType,
        Long categoryId,
        @NotNull(message = "账户ID不能为空") Long accountId,
        Long targetAccountId,
        @NotNull(message = "金额不能为空") @DecimalMin(value = "0.01", message = "金额必须大于0")
        @Digits(integer = 17, fraction = 2, message = "金额最多保留2位小数") BigDecimal amount,
        @NotBlank(message = "标题不能为空") @Size(max = 128, message = "标题长度不能超过128个字符") String title,
        @Size(max = 1000, message = "备注长度不能超过1000个字符") String note,
        @NotBlank(message = "周期类型不能为空")
        @Pattern(regexp = "MONTHLY", message = "当前仅支持每月周期") String recurrenceType,
        @NotNull(message = "执行日不能为空") @Min(1) @Max(31) Integer executionDay,
        @NotNull(message = "是否月末执行不能为空") Boolean monthEnd,
        @NotNull(message = "执行时间不能为空") LocalTime executionTime,
        @NotNull(message = "开始日期不能为空") LocalDate startDate,
        LocalDate endDate,
        @NotBlank(message = "执行方式不能为空")
        @Pattern(regexp = "AUTO|CONFIRM", message = "执行方式不正确") String executionMode,
        @NotNull(message = "版本号不能为空") Integer version
) {
}
