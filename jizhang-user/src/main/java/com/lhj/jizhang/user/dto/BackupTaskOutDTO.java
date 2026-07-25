package com.lhj.jizhang.user.dto;
import java.time.Instant;
public record BackupTaskOutDTO(Long id, String taskNo, Long bookId, String status, String checksum,
                               Integer formatVersion, Instant createdTime, Instant expiredTime) { }
