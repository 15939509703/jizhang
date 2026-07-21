package com.lhj.jizhang.user.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.config.WechatProperties;
import com.lhj.jizhang.user.model.WechatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WechatApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WechatApiClient.class);
    private final WechatProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WechatApiClient(
            WechatProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public WechatSession exchangeCode(String code) {
        WechatSessionResponse response = requestSession(code);
        validateResponse(response);
        return new WechatSession(response.openid(), response.unionid(), response.sessionKey());
    }

    private WechatSessionResponse requestSession(String code) {
        try {
            String responseBody = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(properties.code2SessionUrl())
                            .queryParam("appid", properties.appId())
                            .queryParam("secret", properties.appSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build()
                            .toUri())
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            return objectMapper.readValue(responseBody, WechatSessionResponse.class);
        } catch (RestClientException | JsonProcessingException exception) {
            LOGGER.warn("Wechat code2session request failed: {}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCodes.WECHAT_LOGIN_FAILED, "微信登录服务暂时不可用");
        }
    }

    private void validateResponse(WechatSessionResponse response) {
        if (response == null || response.errorCode() != null && response.errorCode() != 0) {
            Integer errorCode = response == null ? null : response.errorCode();
            LOGGER.warn("Wechat code2session rejected request, errcode={}", errorCode);
            throw new BusinessException(ErrorCodes.WECHAT_LOGIN_FAILED, "微信登录凭证无效或已过期");
        }
        if (response.openid() == null || response.openid().isBlank() || response.sessionKey() == null) {
            throw new BusinessException(ErrorCodes.WECHAT_LOGIN_FAILED, "微信登录响应不完整");
        }
    }
}
