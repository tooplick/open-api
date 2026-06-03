package com.aiopen.platform.modules.setting.service.impl;

import com.aiopen.platform.modules.setting.SettingKeys;
import com.aiopen.platform.modules.setting.dto.PublicSettingsVO;
import com.aiopen.platform.modules.setting.dto.SettingsVO;
import com.aiopen.platform.modules.setting.entity.SystemSetting;
import com.aiopen.platform.modules.setting.mapper.SystemSettingMapper;
import com.aiopen.platform.modules.setting.service.SystemSettingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingServiceImpl
        extends ServiceImpl<SystemSettingMapper, SystemSetting>
        implements SystemSettingService {

    @Override
    public String get(String key, String def) {
        SystemSetting s = getOne(
                Wrappers.<SystemSetting>lambdaQuery().eq(SystemSetting::getConfigKey, key), false);
        return s != null && s.getConfigValue() != null ? s.getConfigValue() : def;
    }

    @Override
    public boolean getBool(String key, boolean def) {
        String v = get(key, null);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private void put(String key, String value) {
        SystemSetting existing = getOne(
                Wrappers.<SystemSetting>lambdaQuery().eq(SystemSetting::getConfigKey, key), false);
        if (existing == null) {
            SystemSetting s = new SystemSetting();
            s.setConfigKey(key);
            s.setConfigValue(value);
            save(s);
        } else {
            existing.setConfigValue(value);
            updateById(existing);
        }
    }

    private void putIfAbsent(String key, String value) {
        long cnt = count(Wrappers.<SystemSetting>lambdaQuery().eq(SystemSetting::getConfigKey, key));
        if (cnt == 0) {
            SystemSetting s = new SystemSetting();
            s.setConfigKey(key);
            s.setConfigValue(value);
            save(s);
        }
    }

    @Override
    public void ensureDefaults() {
        putIfAbsent(SettingKeys.SITE_NAME, "AI Open Platform");
        putIfAbsent(SettingKeys.SITE_SUBTITLE, "大模型聚合开放平台");
        putIfAbsent(SettingKeys.SITE_FOOTER, "AI 模型聚合开放平台");
        putIfAbsent(SettingKeys.LOGIN_ANNOUNCEMENT, "");
        putIfAbsent(SettingKeys.DEFAULT_KEY_GROUP, "default");
        putIfAbsent(SettingKeys.REGISTER_ENABLED, "true");
        putIfAbsent(SettingKeys.REGISTER_PASSWORD, "true");
        putIfAbsent(SettingKeys.REGISTER_EMAIL, "false");
        putIfAbsent(SettingKeys.REGISTER_GITHUB, "false");
    }

    @Override
    public SettingsVO getSettings() {
        SettingsVO vo = new SettingsVO();
        vo.setSiteName(get(SettingKeys.SITE_NAME, "AI Open Platform"));
        vo.setSiteSubtitle(get(SettingKeys.SITE_SUBTITLE, "大模型聚合开放平台"));
        vo.setSiteFooter(get(SettingKeys.SITE_FOOTER, "AI 模型聚合开放平台"));
        vo.setLoginAnnouncement(get(SettingKeys.LOGIN_ANNOUNCEMENT, ""));
        vo.setDefaultKeyGroup(get(SettingKeys.DEFAULT_KEY_GROUP, "default"));
        vo.setRegisterEnabled(getBool(SettingKeys.REGISTER_ENABLED, true));
        vo.setPasswordRegisterEnabled(getBool(SettingKeys.REGISTER_PASSWORD, true));
        vo.setEmailRegisterEnabled(getBool(SettingKeys.REGISTER_EMAIL, false));
        vo.setGithubRegisterEnabled(getBool(SettingKeys.REGISTER_GITHUB, false));
        return vo;
    }

    @Override
    public PublicSettingsVO getPublicSettings() {
        PublicSettingsVO vo = new PublicSettingsVO();
        vo.setSiteName(get(SettingKeys.SITE_NAME, "AI Open Platform"));
        vo.setSiteSubtitle(get(SettingKeys.SITE_SUBTITLE, "大模型聚合开放平台"));
        vo.setLoginAnnouncement(get(SettingKeys.LOGIN_ANNOUNCEMENT, ""));
        vo.setDefaultKeyGroup(get(SettingKeys.DEFAULT_KEY_GROUP, "default"));
        vo.setRegisterEnabled(getBool(SettingKeys.REGISTER_ENABLED, true));
        vo.setPasswordRegisterEnabled(getBool(SettingKeys.REGISTER_PASSWORD, true));
        vo.setEmailRegisterEnabled(getBool(SettingKeys.REGISTER_EMAIL, false));
        vo.setGithubRegisterEnabled(getBool(SettingKeys.REGISTER_GITHUB, false));
        return vo;
    }

    @Override
    @Transactional
    public void updateSettings(SettingsVO req) {
        put(SettingKeys.SITE_NAME, req.getSiteName());
        put(SettingKeys.SITE_SUBTITLE, req.getSiteSubtitle());
        put(SettingKeys.SITE_FOOTER, req.getSiteFooter());
        put(SettingKeys.LOGIN_ANNOUNCEMENT, req.getLoginAnnouncement());
        put(SettingKeys.DEFAULT_KEY_GROUP, req.getDefaultKeyGroup());
        put(SettingKeys.REGISTER_ENABLED, String.valueOf(req.isRegisterEnabled()));
        put(SettingKeys.REGISTER_PASSWORD, String.valueOf(req.isPasswordRegisterEnabled()));
        put(SettingKeys.REGISTER_EMAIL, String.valueOf(req.isEmailRegisterEnabled()));
        put(SettingKeys.REGISTER_GITHUB, String.valueOf(req.isGithubRegisterEnabled()));
    }

    @Override
    public boolean isRegisterEnabled() {
        return getBool(SettingKeys.REGISTER_ENABLED, true);
    }

    @Override
    public boolean isPasswordRegisterEnabled() {
        return getBool(SettingKeys.REGISTER_PASSWORD, true);
    }
}
