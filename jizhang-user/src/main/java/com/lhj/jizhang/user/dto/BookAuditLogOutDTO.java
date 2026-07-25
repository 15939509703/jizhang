package com.lhj.jizhang.user.dto;

import java.time.Instant;

public record BookAuditLogOutDTO(Long id, Long operatorUserId, String action, String objectType,
                                 Long objectId, String summary, Instant createdTime) { }
