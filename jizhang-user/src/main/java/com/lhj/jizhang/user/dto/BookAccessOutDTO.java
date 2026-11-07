package com.lhj.jizhang.user.dto;

import java.util.Map;

/** 当前账本服务端求值后的菜单、动作及账单范围。 */
public record BookAccessOutDTO(
        Long bookId,
        String role,
        Map<String, Boolean> menuPermissions,
        Map<String, Boolean> actionPermissions,
        String transactionScope,
        Long policyVersion
) { }
