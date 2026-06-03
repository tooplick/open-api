package com.aiopen.platform.modules.setting.controller;

import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.setting.dto.PublicSettingsVO;
import com.aiopen.platform.modules.setting.dto.SettingsVO;
import com.aiopen.platform.modules.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置。/public 子集放行供登录页读取;其余仅管理员。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SystemSettingService settingService;

    @GetMapping("/public")
    public Result<PublicSettingsVO> publicSettings() {
        return Result.success(settingService.getPublicSettings());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SettingsVO> get() {
        return Result.success(settingService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@RequestBody SettingsVO request) {
        settingService.updateSettings(request);
        return Result.success();
    }
}
