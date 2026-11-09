package com.lhj.jizhang.user.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class WechatPhoneClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WechatPhoneClient.class);
    private static final String PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";

    private final WechatNotificationClient notificationClient;
    private final RestClient client;

    /** 与订阅消息共用微信 access token 缓存。 */
    public WechatPhoneClient(WechatNotificationClient notificationClient, RestClient.Builder builder) {
        this.notificationClient = notificationClient;
        this.client = builder.build();
    }

    /** 使用微信一次性授权 code 获取经过微信核验的中国大陆手机号。 */
    public String exchangeCode(String code) {
        try {
            PhoneResponse response = client.post()
                    .uri(UriComponentsBuilder.fromUriString(PHONE_URL)
                            .queryParam("access_token", notificationClient.accessToken()).build().toUri())
                    .body(Map.of("code", code))
                    .retrieve().body(PhoneResponse.class);
            if (response == null || response.errorCode() == null || response.errorCode() != 0
                    || response.phoneInfo() == null) {
                throw invalidPhoneCode();
            }
            PhoneInfo info = response.phoneInfo();
            if (!"86".equals(info.countryCode()) || info.purePhoneNumber() == null
                    || !info.purePhoneNumber().matches("^1[3-9]\\d{9}$")) {
                throw invalidPhoneCode();
            }
            return info.purePhoneNumber();
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("Wechat phone request failed: {}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCodes.WECHAT_LOGIN_FAILED, "微信手机号服务暂时不可用");
        }
    }

    /** 将无效、过期或非大陆号码统一转换为业务错误。 */
    private BusinessException invalidPhoneCode() {
        return new BusinessException(ErrorCodes.PHONE_BINDING_INVALID, "微信手机号授权无效，请重新授权");
    }

    private record PhoneInfo(@JsonProperty("purePhoneNumber") String purePhoneNumber,
                             @JsonProperty("countryCode") String countryCode) { }

    private record PhoneResponse(@JsonProperty("errcode") Integer errorCode,
                                 @JsonProperty("phone_info") PhoneInfo phoneInfo) { }
}
