package com.lhj.jizhang.security.model;

public record TokenPair(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
