package com.xiachen.yishe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiachen.yishe.api.config.WechatProperties;
import com.xiachen.yishe.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class WechatClientTest {
    private static final String APPID = "wxc790fc208f0660c4";
    private static final String SECRET = "test-only-private-secret";
    private static final String CODE = "test-only-login-code";
    private static final String URL = "https://api.weixin.qq.com/sns/jscode2session?appid=" + APPID
            + "&secret=" + SECRET + "&js_code=" + CODE + "&grant_type=authorization_code";

    private MockRestServiceServer server;
    private WechatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        WechatProperties properties = new WechatProperties();
        WechatProperties.App app = new WechatProperties.App();
        app.setAppid(APPID);
        app.setSecret(SECRET);
        properties.setApps(List.of(app));
        client = new WechatClient(builder.build(), properties, new ObjectMapper());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain;charset=UTF-8", "application/json"})
    void acceptsWechatJsonRegardlessOfResponseMediaType(String mediaType) {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"openid\":\"test-openid\",\"unionid\":\"test-unionid\",\"session_key\":\"private-session\",\"extra\":true}",
                MediaType.parseMediaType(mediaType)));

        assertThat(client.code2Session(APPID, CODE)).isEqualTo(new WechatClient.Session("test-openid", "test-unionid"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/json"})
    void rejectsWechatErrorsAndLogsOnlyErrorCode(String mediaType, CapturedOutput output) {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"errcode\":40125,\"errmsg\":\"invalid " + SECRET + "\",\"openid\":\"must-not-log-in\"}",
                MediaType.parseMediaType(mediaType)));

        assertThatThrownBy(() -> client.code2Session(APPID, CODE)).isInstanceOf(BizException.class);
        assertThat(output.getAll()).contains("errcode=40125").doesNotContain(SECRET, CODE);
        server.verify();
    }

    @Test
    void rejectsHttpFailuresWithoutLoggingCredentialBearingUrl(CapturedOutput output) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_PLAIN).body("upstream failure " + SECRET));

        assertThatThrownBy(() -> client.code2Session(APPID, CODE)).isInstanceOf(BizException.class);
        assertThat(output.getAll()).contains("exception=BadGateway").doesNotContain(SECRET, CODE, URL);
        server.verify();
    }

    @Test
    void rejectsMalformedJsonWithoutLoggingResponseBody(CapturedOutput output) {
        server.expect(requestTo(URL)).andRespond(withSuccess("not-json-" + SECRET, MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.code2Session(APPID, CODE)).isInstanceOf(BizException.class);
        assertThat(output.getAll()).contains("jscode2session request failed").doesNotContain(SECRET, CODE);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"errcode\":0,\"openid\":\"\"}"})
    void requiresNonemptyOpenid(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.code2Session(APPID, CODE)).isInstanceOf(BizException.class);
        server.verify();
    }
}
