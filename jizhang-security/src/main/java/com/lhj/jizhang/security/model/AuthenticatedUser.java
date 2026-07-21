package com.lhj.jizhang.security.model;

public record AuthenticatedUser(Long userId, Integer sessionVersion) {
}
