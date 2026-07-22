package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotNull;

public record CategoryVisibilityInDTO(
        @NotNull(message = "隐藏状态不能为空") Boolean hidden
) {
}
