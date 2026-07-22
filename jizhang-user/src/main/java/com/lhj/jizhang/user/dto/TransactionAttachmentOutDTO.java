package com.lhj.jizhang.user.dto;

public record TransactionAttachmentOutDTO(
        Long id,
        String fileName,
        String contentType,
        Long fileSize,
        String downloadUrl
) {
}
