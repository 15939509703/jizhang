package com.lhj.jizhang.user.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WechatSessionResponse(
        String openid,
        String unionid,
        @JsonProperty("session_key") String sessionKey,
        @JsonProperty("errcode") Integer errorCode,
        @JsonProperty("errmsg") String errorMessage
) {
}
