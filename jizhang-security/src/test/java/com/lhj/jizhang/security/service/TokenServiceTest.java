package com.lhj.jizhang.security.service;

import com.lhj.jizhang.security.config.JwtProperties;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.security.model.TokenPair;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {
    @Test
    void shouldIssueAndParseAccessToken() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        JwtProperties properties = new JwtProperties(
                "jizhang-test",
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()),
                Duration.ofMinutes(30),
                Duration.ofDays(30)
        );
        TokenService tokenService = new TokenService(properties, redisTemplate);

        TokenPair tokenPair = tokenService.issue(101L, 3);
        AuthenticatedUser authenticatedUser = tokenService.parseAccessToken(tokenPair.accessToken());

        assertEquals(101L, authenticatedUser.userId());
        assertEquals(3, authenticatedUser.sessionVersion());
        assertEquals(1800L, tokenPair.expiresIn());
        assertFalse(tokenPair.refreshToken().isBlank());
        verify(valueOperations).set(anyString(), eq("101:3"), eq(Duration.ofDays(30)));
    }
}
