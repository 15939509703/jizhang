package com.lhj.jizhang.user.dto;

public record BookMemberOutDTO(
        Long id,
        Long userId,
        String userNo,
        String nickName,
        String avatarUrl,
        String role,
        Integer status
) {
}
