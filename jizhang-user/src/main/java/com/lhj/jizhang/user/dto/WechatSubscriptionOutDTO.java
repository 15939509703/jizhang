package com.lhj.jizhang.user.dto;
public record WechatSubscriptionOutDTO(String scene, boolean enabled, Integer availableAuthorizations,
                                       String templateId, boolean serviceEnabled) { }
