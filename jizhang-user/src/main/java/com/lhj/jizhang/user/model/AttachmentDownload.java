package com.lhj.jizhang.user.model;

import org.springframework.core.io.Resource;

public record AttachmentDownload(
        Resource resource,
        String fileName,
        String contentType
) {
}
