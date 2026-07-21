package com.lhj.jizhang.user.model;

public record WechatSession(
        String openid,
        String unionid,
        String sessionKey
) {
}
