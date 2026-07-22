package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BookMemberRoleInDTO(
        @NotBlank(message = "成员角色不能为空")
        @Pattern(regexp = "ADMIN|MEMBER|VIEWER", message = "成员角色不正确") String role
) {
}
