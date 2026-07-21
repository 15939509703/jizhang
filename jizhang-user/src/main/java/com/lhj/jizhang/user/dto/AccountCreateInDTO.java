package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "AccountCreateRequest", description = "创建账户请求")
public record AccountCreateInDTO(
        @Schema(description = "账本ID", example = "1")
        @NotNull(message = "账本ID不能为空") Long bookId,
        @Schema(description = "账户名称", example = "微信零钱")
        @NotBlank(message = "账户名称不能为空")
        @Size(max = 64, message = "账户名称长度不能超过64个字符")
        String name,
        @Schema(description = "账户类型", example = "WECHAT",
                allowableValues = {"CASH", "BANK", "WECHAT", "ALIPAY", "CREDIT"})
        @NotBlank(message = "账户类型不能为空")
        @Pattern(regexp = "CASH|BANK|WECHAT|ALIPAY|CREDIT", message = "账户类型不正确")
        String accountType,
        @Schema(description = "账户性质", example = "ASSET", allowableValues = {"ASSET", "LIABILITY"})
        @NotBlank(message = "账户性质不能为空")
        @Pattern(regexp = "ASSET|LIABILITY", message = "账户性质不正确")
        String accountNature,
        @Schema(description = "初始余额", example = "1000.00")
        @NotNull(message = "初始余额不能为空")
        @Digits(integer = 17, fraction = 2, message = "初始余额最多保留2位小数")
        BigDecimal initialBalance,
        @Schema(description = "是否计入总资产", example = "true")
        @NotNull(message = "是否计入总资产不能为空") Boolean includedInAssets
) {
}
