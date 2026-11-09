package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "WechatPhoneCodeRequest", description = "微信手机号授权凭证")
public record WechatPhoneCodeInDTO(
        @Schema(description = "wx.getPhoneNumber 返回的一次性 code")
        @NotBlank(message = "微信手机号授权凭证不能为空")
        @Size(max = 256, message = "微信手机号授权凭证过长")
        String code
) { }
