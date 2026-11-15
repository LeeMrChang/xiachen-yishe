package com.xiachen.yishe.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    /** 本地/体验版没有真实 AppID 密钥时开启，按 X-App-Id 生成固定的模拟 openid。 */
    private boolean mock;

    private List<App> apps = new ArrayList<>();

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public List<App> getApps() {
        return apps;
    }

    public void setApps(List<App> apps) {
        this.apps = apps == null ? new ArrayList<>() : apps;
    }

    public Optional<App> find(String appid) {
        return apps.stream()
                .filter(app -> app.getAppid() != null && app.getAppid().equals(appid))
                .findFirst();
    }

    public static class App {

        private String appid;

        private String secret;

        private String label;

        public String getAppid() {
            return appid;
        }

        public void setAppid(String appid) {
            this.appid = appid;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
