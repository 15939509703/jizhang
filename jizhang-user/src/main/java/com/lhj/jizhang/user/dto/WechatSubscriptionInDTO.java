package com.lhj.jizhang.user.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
public record WechatSubscriptionInDTO(
        @NotBlank @Pattern(regexp = "RECURRING_PENDING|BUDGET_ALERT") String scene,
        @NotNull Boolean enabled,
        @NotNull Boolean authorized
) { }
