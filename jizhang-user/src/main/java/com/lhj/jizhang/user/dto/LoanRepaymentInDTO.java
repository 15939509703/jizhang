package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "LoanRepaymentRequest", description = "登记还款请求")
public record LoanRepaymentInDTO(
        @Schema(description = "本次还款金额", example = "100.00")
        @NotNull(message = "还款金额不能为空")
        @DecimalMin(value = "0.01", message = "还款金额必须大于0")
        @Digits(integer = 17, fraction = 2, message = "金额最多保留2位小数")
        BigDecimal amount,
        @Schema(description = "备注", example = "微信已收款")
        @Size(max = 200, message = "还款备注长度不能超过200个字符")
        String note
) {
}
