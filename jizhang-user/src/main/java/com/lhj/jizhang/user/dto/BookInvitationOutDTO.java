package com.lhj.jizhang.user.dto;

import java.time.Instant;

public record BookInvitationOutDTO(
        Long id, String invitationNo, Long bookId, String bookName, String inviterName,
        String role, String status, Instant expiredTime, String token
) { }
