package com.lhj.jizhang.user.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class WechatSessionStore {
    private static final String KEY_PREFIX = "jizhang:wechat:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private final StringRedisTemplate redisTemplate;

    public WechatSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long userId, String sessionKey) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, sessionKey, SESSION_TTL);
    }
}
