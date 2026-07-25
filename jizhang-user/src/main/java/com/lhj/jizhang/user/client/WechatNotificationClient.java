package com.lhj.jizhang.user.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lhj.jizhang.user.config.WechatNotificationProperties;
import com.lhj.jizhang.user.config.WechatProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class WechatNotificationClient {
    private final WechatProperties wechat;
    private final WechatNotificationProperties notification;
    private final RestClient client;
    private volatile CachedToken cachedToken;

    public WechatNotificationClient(WechatProperties wechat, WechatNotificationProperties notification,
                                    RestClient.Builder builder) {
        this.wechat = wechat; this.notification = notification; this.client = builder.build();
    }

    public SendResult send(String openid, String templateId, String pagePath, Map<String, Object> data) {
        if (!notification.enabled()) return new SendResult(false, -1, "DISABLED");
        SendResponse response = client.post().uri(UriComponentsBuilder.fromUriString(notification.sendUrl())
                        .queryParam("access_token", accessToken()).build().toUri())
                .body(Map.of("touser", openid, "template_id", templateId, "page", pagePath, "data", data))
                .retrieve().body(SendResponse.class);
        int code = response == null || response.errcode() == null ? -1 : response.errcode();
        return new SendResult(code == 0, code, response == null ? "EMPTY_RESPONSE" : response.errmsg());
    }

    private synchronized String accessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && cachedToken.expiresAt > now + 120_000) return cachedToken.value;
        TokenResponse response = client.get().uri(UriComponentsBuilder.fromUriString(notification.tokenUrl())
                .queryParam("grant_type", "client_credential").queryParam("appid", wechat.appId())
                .queryParam("secret", wechat.appSecret()).build().toUri()).retrieve().body(TokenResponse.class);
        if (response == null || response.accessToken() == null) throw new IllegalStateException("Wechat token unavailable");
        cachedToken = new CachedToken(response.accessToken(), now + Math.max(300, response.expiresIn()) * 1000L);
        return cachedToken.value;
    }

    private record CachedToken(String value, long expiresAt) { }
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") int expiresIn) { }
    private record SendResponse(Integer errcode, String errmsg) { }
    public record SendResult(boolean success, int errorCode, String message) { }
}
