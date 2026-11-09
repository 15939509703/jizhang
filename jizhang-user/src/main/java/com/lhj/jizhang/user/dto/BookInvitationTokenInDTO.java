package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 通过请求体提交邀请码，避免将一次性凭证写入 URL。
 *
 * @param token 一次性邀请码
 */
public record BookInvitationTokenInDTO(@NotBlank String token) { }
