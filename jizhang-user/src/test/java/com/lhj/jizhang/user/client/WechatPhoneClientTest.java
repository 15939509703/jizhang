package com.lhj.jizhang.user.client;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.config.WechatNotificationProperties;
import com.lhj.jizhang.user.config.WechatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatPhoneClientTest {
    private MockRestServiceServer server;
    private WechatPhoneClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        WechatProperties wechat = new WechatProperties("app-id", "app-secret", "unused");
        WechatNotificationProperties notification = new WechatNotificationProperties(
                false, "", "", "https://api.weixin.qq.com/cgi-bin/token", "");
        WechatNotificationClient notificationClient = new WechatNotificationClient(
                wechat, notification, builder, new ObjectMapper());
        client = new WechatPhoneClient(notificationClient, builder);
    }

    @Test
    void returnsVerifiedMainlandPhone() {
        expectToken();
        server.expect(requestTo(containsString("getuserphonenumber")))
                .andRespond(withSuccess("{\"errcode\":0,\"phone_info\":{\"purePhoneNumber\":\"13800138000\",\"countryCode\":\"86\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals("13800138000", client.exchangeCode("phone-code"));
        server.verify();
    }

    @Test
    void rejectsExpiredPhoneCode() {
        expectToken();
        server.expect(requestTo(containsString("getuserphonenumber")))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.exchangeCode("expired-code"));

        assertEquals(ErrorCodes.PHONE_BINDING_INVALID, exception.getCode());
        server.verify();
    }

    /** 为手机号接口准备模拟微信接口令牌。 */
    private void expectToken() {
        server.expect(requestTo(containsString("cgi-bin/token")))
                .andRespond(withSuccess("{\"access_token\":\"test-token\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
    }
}
