package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "WechatLoginRequest", description = "微信小程序登录请求")
public record WechatLoginInDTO(
        @Schema(description = "wx.login返回的临时登录凭证", example = "0a1b2c3d4e5f")
        @NotBlank(message = "微信登录code不能为空") String code,
        @Schema(description = "用户昵称", example = "小明")
        @Size(max = 64, message = "昵称长度不能超过64个字符") String nickName,
        @Schema(description = "用户头像地址", example = "https://example.com/avatar.png")
        @Size(max = 512, message = "头像地址长度不能超过512个字符") String avatarUrl
) {
}
