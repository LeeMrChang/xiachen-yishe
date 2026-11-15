package com.xiachen.yishe.api.service;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    public WechatClient(RestClient wechatRestClient, WechatProperties properties) {
        this.restClient = wechatRestClient;
        this.properties = properties;
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
            response = restClient.get()
                    .uri(builder -> builder.scheme("https").host("api.weixin.qq.com")
                            .path("/sns/jscode2session")
                            .queryParam("appid", appid)
                            .queryParam("secret", app.getSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(SessionResponse.class);
        } catch (Exception e) {
            log.warn("jscode2session request failed, appid={}", appid, e);
            throw new BizException(ErrorCode.WX_LOGIN_FAILED);
        }
        if (response == null || response.openid() == null || response.openid().isBlank()) {
            log.warn("jscode2session rejected, appid={} errcode={} errmsg={}", appid,
                    response == null ? null : response.errcode(),
                    response == null ? null : response.errmsg());
            throw new BizException(ErrorCode.WX_LOGIN_FAILED);
        }
        return new Session(response.openid(), response.unionid());
    }

    public record Session(String openid, String unionid) {
    }

    private record SessionResponse(String openid,
                                   @JsonProperty("session_key") String sessionKey,
                                   String unionid,
                                   Integer errcode,
                                   String errmsg) {
    }
}
