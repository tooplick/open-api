package com.aiopen.platform.config;

import com.aiopen.platform.modules.setting.SettingKeys;
import com.aiopen.platform.modules.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OutboundHttpClientFactory {

    private final SystemSettingService settingService;

    public HttpClient create(Duration connectTimeout, HttpClient.Version version) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout);
        if (version != null) {
            builder.version(version);
        }

        if (settingService.getBool(SettingKeys.PROXY_ENABLED, false)) {
            String host = settingService.get(SettingKeys.PROXY_HOST, "");
            int port = parseInt(settingService.get(SettingKeys.PROXY_PORT, "7890"), 7890);
            if (StringUtils.hasText(host) && port > 0) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(host.trim(), port)));
            }
        }
        return builder.build();
    }

    private int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return def;
        }
    }
}
