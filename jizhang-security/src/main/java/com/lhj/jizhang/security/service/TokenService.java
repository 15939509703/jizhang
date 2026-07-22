package com.lhj.jizhang.security.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.security.config.JwtProperties;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.security.model.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String REFRESH_TOKEN_PREFIX = "jizhang:auth:refresh:";

    private final JwtProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final SecretKey signingKey;

    public TokenService(JwtProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secretBase64()));
    }

    public TokenPair issue(Long userId, Integer sessionVersion) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        String accessToken = createAccessToken(userId, sessionVersion, issuedAt, expiresAt);
        String refreshToken = createRefreshToken();
        saveRefreshToken(refreshToken, userId, sessionVersion);
        return new TokenPair(accessToken, refreshToken, properties.accessTokenTtl().toSeconds());
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthenticatedUser(Long.valueOf(claims.getSubject()), claims.get("sv", Integer.class));
    }

    public TokenPair refresh(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + sha256(refreshToken);
        String tokenContext = redisTemplate.opsForValue().getAndDelete(key);
        if (tokenContext == null) {
            throw unauthorizedRefreshToken();
        }
        String[] parts = tokenContext.split(":", 2);
        if (parts.length != 2) {
            throw unauthorizedRefreshToken();
        }
        try {
            return issue(Long.valueOf(parts[0]), Integer.valueOf(parts[1]));
        } catch (NumberFormatException exception) {
            throw unauthorizedRefreshToken();
        }
    }

    private String createAccessToken(Long userId, Integer sessionVersion, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim("sv", sessionVersion)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    private String createRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void saveRefreshToken(String refreshToken, Long userId, Integer sessionVersion) {
        String key = REFRESH_TOKEN_PREFIX + sha256(refreshToken);
        String value = userId + ":" + sessionVersion;
        redisTemplate.opsForValue().set(key, value, properties.refreshTokenTtl());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BusinessException unauthorizedRefreshToken() {
        return new BusinessException(ErrorCodes.UNAUTHORIZED, "刷新令牌无效或已过期", HttpStatus.UNAUTHORIZED);
    }
}
