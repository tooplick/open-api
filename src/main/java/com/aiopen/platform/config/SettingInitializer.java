package com.aiopen.platform.config;

import com.aiopen.platform.modules.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时确保系统设置默认项存在(不覆盖已有值)。
 */
@Order(1)
@Component
@RequiredArgsConstructor
public class SettingInitializer implements CommandLineRunner {

    private final SystemSettingService settingService;

    @Override
    public void run(String... args) {
        settingService.ensureDefaults();
    }
}
