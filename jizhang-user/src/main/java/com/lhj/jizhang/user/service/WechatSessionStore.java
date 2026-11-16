package com.lhj.jizhang.user.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class WechatSessionStore {
    private static final String KEY_PREFIX = "jizhang:wechat:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private final StringRedisTemplate redisTemplate;

    /**
     * 初始化微信会话服务。
     *
     * @param redisTemplate Redis 操作器
     */
    public WechatSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存微信会话密钥。
     *
     * @param userId 用户ID
     * @param sessionKey 微信会话密钥
     */
    public void save(Long userId, String sessionKey) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, sessionKey, SESSION_TTL);
    }
}
