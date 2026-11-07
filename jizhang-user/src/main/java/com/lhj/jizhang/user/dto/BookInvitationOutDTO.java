package com.lhj.jizhang.user.dto;

import java.time.Instant;

/** 邀请预览、创建和接受前展示同一份有效策略快照。 */
public record BookInvitationOutDTO(
        Long id, String invitationNo, Long bookId, String bookName, String inviterName,
        String role, String status, Instant expiredTime, String token, BookAccessOutDTO access
) { }
