package com.lhj.jizhang.user.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.user.config.WechatNotificationProperties;
import com.lhj.jizhang.user.config.WechatProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatNotificationClientTest {
    private MockRestServiceServer server;
    private WechatNotificationClient client;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    /** 初始化模拟微信接口和日志收集器。 */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WechatNotificationClient(
                new WechatProperties("test-app-id", "sensitive-app-secret", "unused"),
                new WechatNotificationProperties(true, "", "test-template-id",
                        "https://api.weixin.qq.com/cgi-bin/token",
                        "https://api.weixin.qq.com/cgi-bin/message/subscribe/send"), builder, new ObjectMapper());
        logger = (Logger) LoggerFactory.getLogger(WechatNotificationClient.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    /** 停止收集并移除测试日志。 */
    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    /** 验证发送请求符合微信文档且 Content-Length 等于 JSON 字节数。 */
    @Test
    void shouldSendJsonWithContentLength() {
        server.expect(requestTo(containsString("grant_type=client_credential")))
                .andRespond(withSuccess("{\"access_token\":\"sensitive-access-token\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/message/subscribe/send"
                        + "?access_token=sensitive-access-token"))
                .andExpect(content().json("""
                        {"touser":"test-openid","template_id":"test-template-id",
                         "page":"pages/budget/budget","data":{"thing4":{"value":"预算提醒"}}}
                        """))
                .andExpect(request -> {
                    assertEquals(MediaType.APPLICATION_JSON, request.getHeaders().getContentType());
                    assertEquals(((MockClientHttpRequest) request).getBodyAsBytes().length,
                            request.getHeaders().getContentLength());
                })
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        WechatNotificationClient.SendResult result = client.send("test-openid", "test-template-id",
                "pages/budget/budget", Map.of("thing4", Map.of("value", "预算提醒")));

        assertTrue(result.success());
        assertEquals(0, result.errorCode());
        server.verify();
    }

    /** 发送接口出错时只记录阶段、异常类型和 HTTP 状态。 */
    @Test
    void shouldLogSendFailureWithoutSensitiveValues() {
        server.expect(requestTo(containsString("grant_type=client_credential")))
                .andRespond(withSuccess("{\"access_token\":\"sensitive-access-token\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/message/subscribe/send")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("sensitive-response-body"));

        assertThrows(RestClientResponseException.class,
                () -> client.send("sensitive-openid", "test-template-id", "/pages/budget/budget",
                        Map.of("thing4", Map.of("value", "sensitive-message"))));

        String logged = appender.list.getFirst().getFormattedMessage();
        assertTrue(logged.contains("stage=SEND"));
        assertTrue(logged.contains("httpStatus=400"));
        assertFalse(logged.contains("sensitive-"));
        server.verify();
    }

    /** 微信返回 HTTP 200 时保留业务错误码与错误信息。 */
    @Test
    void shouldReturnWechatBusinessError() {
        server.expect(requestTo(containsString("grant_type=client_credential")))
                .andRespond(withSuccess("{\"access_token\":\"sensitive-access-token\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/message/subscribe/send")))
                .andRespond(withSuccess("{\"errcode\":43101,\"errmsg\":\"user refuse to accept the msg\"}",
                        MediaType.APPLICATION_JSON));

        WechatNotificationClient.SendResult result = client.send("sensitive-openid", "test-template-id",
                "/pages/budget/budget", Map.of());

        assertFalse(result.success());
        assertEquals(43101, result.errorCode());
        assertEquals("user refuse to accept the msg", result.message());
        server.verify();
    }

    /** 获取 token 出错时不记录带有 AppSecret 的请求地址。 */
    @Test
    void shouldLogTokenFailureWithoutAppSecret() {
        server.expect(requestTo(containsString("grant_type=client_credential")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("sensitive-response-body"));

        assertThrows(RestClientResponseException.class,
                () -> client.send("sensitive-openid", "test-template-id", "/pages/budget/budget", Map.of()));

        String logged = appender.list.getFirst().getFormattedMessage();
        assertTrue(logged.contains("stage=TOKEN"));
        assertTrue(logged.contains("httpStatus=502"));
        assertFalse(logged.contains("sensitive-"));
        server.verify();
    }
}
