package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookMemberInviteInDTO(
        @NotBlank(message = "用户编号不能为空")
        @Size(max = 32, message = "用户编号长度不能超过32个字符") String userNo,
        @NotBlank(message = "成员角色不能为空")
        @Pattern(regexp = "ADMIN|MEMBER|VIEWER", message = "成员角色不正确") String role
) {
}
