package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginUser", description = "登录用户信息")
public record LoginUserOutDTO(
        @Schema(description = "用户ID", example = "1")
        Long id,
        @Schema(description = "用户编号", example = "USR202607210001")
        String userNo,
        @Schema(description = "用户昵称", example = "小明")
        String nickName,
        @Schema(description = "头像地址", example = "https://example.com/avatar.png")
        String avatarUrl
) {
}
