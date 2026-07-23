package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "LoanUpdateRequest", description = "修改借还记录请求")
public record LoanUpdateInDTO(
        @Schema(description = "往来人名称", example = "张三")
        @NotBlank(message = "往来人不能为空")
        @Size(max = 128, message = "往来人长度不能超过128个字符")
        String counterpartyName,
        @Schema(description = "借还总金额", example = "500.00")
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.01", message = "金额必须大于0")
        @Digits(integer = 17, fraction = 2, message = "金额最多保留2位小数")
        BigDecimal totalAmount,
        @Schema(description = "到期日", example = "2026-08-01")
        LocalDate dueDate,
        @Schema(description = "状态", example = "OPEN", allowableValues = {"OPEN", "PARTIAL", "CLOSED"})
        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "OPEN|PARTIAL|CLOSED", message = "状态不正确")
        String status,
        @Schema(description = "备注", example = "午餐垫付")
        @Size(max = 1000, message = "备注长度不能超过1000个字符")
        String note,
        @Schema(description = "乐观锁版本号", example = "1")
        @NotNull(message = "版本号不能为空")
        Integer version
) {
}
