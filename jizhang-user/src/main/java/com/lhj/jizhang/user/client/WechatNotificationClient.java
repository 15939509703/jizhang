package com.lhj.jizhang.user.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.user.config.WechatNotificationProperties;
import com.lhj.jizhang.user.config.WechatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class WechatNotificationClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WechatNotificationClient.class);
    private final WechatProperties wechat;
    private final WechatNotificationProperties notification;
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private volatile CachedToken cachedToken;

    public WechatNotificationClient(WechatProperties wechat, WechatNotificationProperties notification,
                                    RestClient.Builder builder, ObjectMapper objectMapper) {
        this.wechat = wechat;
        this.notification = notification;
        this.client = builder.build();
        this.objectMapper = objectMapper;
    }

    public SendResult send(String openid, String templateId, String pagePath, Map<String, Object> data) {
        if (!notification.enabled()) return new SendResult(false, -1, "DISABLED");
        String token;
        try {
            token = accessToken();
        } catch (RuntimeException exception) {
            logRequestFailure("TOKEN", exception);
            throw exception;
        }
        ResponseEntity<SendResponse> httpResponse;
        try {
            byte[] requestBody = serializePayload(Map.of(
                    "touser", openid, "template_id", templateId, "page", pagePath, "data", data));
            httpResponse = client.post().uri(UriComponentsBuilder.fromUriString(notification.sendUrl())
                            .queryParam("access_token", token).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON).contentLength(requestBody.length)
                    .body(requestBody)
                    .retrieve().toEntity(SendResponse.class);
        } catch (RuntimeException exception) {
            logRequestFailure("SEND", exception);
            throw exception;
        }
        SendResponse response = httpResponse.getBody();
        int code = response == null || response.errcode() == null ? -1 : response.errcode();
        return new SendResult(code == 0, code, response == null ? "EMPTY_RESPONSE" : response.errmsg());
    }

    /**
     * 将订阅消息请求序列化为字节，确保请求体与 Content-Length 一致。
     *
     * @param payload 订阅消息请求字段
     * @return UTF-8 编码的 JSON 请求体
     */
    private byte[] serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("微信订阅消息序列化失败", exception);
        }
    }

    /**
     * 只记录请求阶段和错误类型，避免异常信息中包含 token、AppSecret 或消息内容。
     */
    private void logRequestFailure(String stage, RuntimeException exception) {
        Integer httpStatus = exception instanceof RestClientResponseException response
                ? response.getStatusCode().value() : null;
        LOGGER.warn("微信订阅消息请求异常 stage={} exceptionType={} causeType={} httpStatus={}",
                stage, exception.getClass().getName(),
                exception.getCause() == null ? "none" : exception.getCause().getClass().getName(), httpStatus);
    }

    private synchronized String accessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && cachedToken.expiresAt > now + 120_000) return cachedToken.value;
        TokenResponse response = client.get().uri(UriComponentsBuilder.fromUriString(notification.tokenUrl())
                .queryParam("grant_type", "client_credential").queryParam("appid", wechat.appId())
                .queryParam("secret", wechat.appSecret()).build().toUri()).retrieve().body(TokenResponse.class);
        if (response == null || response.accessToken() == null)
            throw new IllegalStateException("Wechat token unavailable");
        cachedToken = new CachedToken(response.accessToken(), now + Math.max(300, response.expiresIn()) * 1000L);
        return cachedToken.value;
    }

    private record CachedToken(String value, long expiresAt) {
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") int expiresIn) {
    }

    private record SendResponse(Integer errcode, String errmsg) {
    }

    public record SendResult(boolean success, int errorCode, String message) {
    }
}
