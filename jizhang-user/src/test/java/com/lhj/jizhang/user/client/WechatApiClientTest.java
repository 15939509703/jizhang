package com.lhj.jizhang.user.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.config.WechatProperties;
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

class WechatApiClientTest {
    private MockRestServiceServer server;
    private WechatApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        WechatProperties properties = new WechatProperties(
                "test-app-id",
                "test-app-secret",
                "https://api.weixin.qq.com/sns/jscode2session"
        );
        client = new WechatApiClient(properties, builder, new ObjectMapper());
    }

    @Test
    void shouldRejectWechatErrorResponse() {
        server.expect(requestTo(containsString("js_code=expired-code")))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.TEXT_PLAIN));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.exchangeCode("expired-code")
        );

        assertEquals(ErrorCodes.WECHAT_LOGIN_FAILED, exception.getCode());
        assertEquals("微信登录凭证无效或已过期", exception.getMessage());
        server.verify();
    }
}
