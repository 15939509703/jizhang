package com.lhj.jizhang.user.dto;

import java.time.Instant;

/**
 * @param operatorNickName 操作者昵称
 * @param operatorUserNo 操作者编码
 */
public record BookAuditLogOutDTO(Long id, Long operatorUserId, String action, String objectType,
                                 Long objectId, String summary, Instant createdTime,
                                 String operatorNickName, String operatorUserNo) { }
