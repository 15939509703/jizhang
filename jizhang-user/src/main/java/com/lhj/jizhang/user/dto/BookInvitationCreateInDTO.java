package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

/**
 * 创建冻结成员策略的一次性邀请。
 *
 * @param role 邀请角色
 * @param expiresInHours 邀请有效小时数
 * @param menuPermissions 完整菜单配置
 * @param actionPermissions 完整有效动作配置
 * @param transactionScope ALL 或 OWN_CREATED
 */
public record BookInvitationCreateInDTO(
        @NotBlank @Pattern(regexp = "ADMIN|MEMBER|VIEWER") String role,
        @NotNull @Min(1) @Max(168) Integer expiresInHours,
        Map<String, Boolean> menuPermissions,
        Map<String, Boolean> actionPermissions,
        String transactionScope
) { }
