package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * 成员角色、菜单、操作和账单范围的整体更新请求。
 *
 * @param expectedVersion 前一次读取到的策略版本
 * @param role 成员新角色，不可指定 OWNER
 * @param menuPermissions 完整的菜单键及开启状态
 * @param actionPermissions 完整的操作键及开启状态
 * @param transactionScope ALL 或 OWN_CREATED
 */
public record BookPolicyUpdateInDTO(
        @NotNull Long expectedVersion,
        @NotBlank @Pattern(regexp = "ADMIN|MEMBER|VIEWER") String role,
        @NotNull Map<String, Boolean> menuPermissions,
        @NotNull Map<String, Boolean> actionPermissions,
        @NotBlank @Pattern(regexp = "ALL|OWN_CREATED") String transactionScope
) { }
