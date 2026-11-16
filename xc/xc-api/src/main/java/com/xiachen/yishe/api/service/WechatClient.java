package com.xiachen.yishe.api.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiachen.yishe.api.config.WechatProperties;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WechatClient {

    private static final Logger log = LoggerFactory.getLogger(WechatClient.class);

    private final RestClient restClient;
    private final WechatProperties properties;
    private final ObjectMapper objectMapper;

    public WechatClient(RestClient wechatRestClient, WechatProperties properties, ObjectMapper objectMapper) {
        this.restClient = wechatRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Session code2Session(String appid, String code) {
        if (appid == null || appid.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少 X-App-Id 请求头");
        }
        if (properties.isMock()) {
            return new Session("mock_" + appid, null);
        }
        WechatProperties.App app = properties.find(appid)
                .orElseThrow(() -> new BizException(ErrorCode.WX_LOGIN_FAILED, "未注册的小程序 AppID"));
        if (app.getSecret() == null || app.getSecret().isBlank()) {
            throw new BizException(ErrorCode.WX_LOGIN_FAILED, "小程序密钥未配置");
        }
        SessionResponse response;
        try {
            // WeChat also serves JSON as text/plain. Read the body independently
            // of its media type, then validate it with the application JSON mapper.
            String body = restClient.get()
                    .uri(builder -> builder.scheme("https").host("api.weixin.qq.com")
                            .path("/sns/jscode2session")
                            .queryParam("appid", appid)
                            .queryParam("secret", app.getSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            response = body == null || body.isBlank() ? null : objectMapper.readValue(body, SessionResponse.class);
        } catch (Exception e) {
            // HTTP exceptions may contain the request URL (AppSecret + code),
            // and JSON exceptions may contain the session response. Log neither.
            log.warn("jscode2session request failed, appid={} exception={}", appid, e.getClass().getSimpleName());
            throw new BizException(ErrorCode.WX_LOGIN_FAILED);
        }
        if (response == null || (response.errcode() != null && response.errcode() != 0)
                || response.openid() == null || response.openid().isBlank()) {
            log.warn("jscode2session rejected, appid={} errcode={}", appid,
                    response == null ? null : response.errcode());
            throw new BizException(ErrorCode.WX_LOGIN_FAILED);
        }
        return new Session(response.openid(), response.unionid());
    }

    public record Session(String openid, String unionid) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SessionResponse(String openid,
                                   @JsonProperty("session_key") String sessionKey,
                                   String unionid,
                                   Integer errcode,
                                   String errmsg) {
    }
}
