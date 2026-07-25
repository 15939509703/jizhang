package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PhoneBindingResponse", description = "当前用户手机号绑定状态")
public record PhoneBindingOutDTO(
        @Schema(description = "是否已绑定手机号")
        boolean bound,
        @Schema(description = "已绑定手机号，未绑定时为空", example = "13800138000")
        String phone
) {
}
