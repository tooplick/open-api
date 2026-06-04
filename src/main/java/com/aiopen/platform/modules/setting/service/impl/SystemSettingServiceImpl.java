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
import org.springframework.util.StringUtils;

@Service
public class SystemSettingServiceImpl
        extends ServiceImpl<SystemSettingMapper, SystemSetting>
        implements SystemSettingService {

    private static final String SMTP_PASSWORD_MASK = "******";

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
        putIfAbsent(SettingKeys.SMTP_HOST, "");
        putIfAbsent(SettingKeys.SMTP_PORT, "587");
        putIfAbsent(SettingKeys.SMTP_USERNAME, "");
        putIfAbsent(SettingKeys.SMTP_PASSWORD, "");
        putIfAbsent(SettingKeys.SMTP_FROM, "");
        putIfAbsent(SettingKeys.SMTP_SSL_ENABLED, "false");
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
        vo.setSmtpHost(get(SettingKeys.SMTP_HOST, ""));
        vo.setSmtpPort(parseInt(get(SettingKeys.SMTP_PORT, "587"), 587));
        vo.setSmtpUsername(get(SettingKeys.SMTP_USERNAME, ""));
        String smtpPwd = get(SettingKeys.SMTP_PASSWORD, "");
        vo.setSmtpPassword(StringUtils.hasText(smtpPwd) ? SMTP_PASSWORD_MASK : "");
        vo.setSmtpFrom(get(SettingKeys.SMTP_FROM, ""));
        vo.setSmtpSslEnabled(getBool(SettingKeys.SMTP_SSL_ENABLED, false));
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
        put(SettingKeys.SMTP_HOST, req.getSmtpHost());
        put(SettingKeys.SMTP_PORT, String.valueOf(req.getSmtpPort() == null ? 587 : req.getSmtpPort()));
        put(SettingKeys.SMTP_USERNAME, req.getSmtpUsername());
        String incomingPwd = req.getSmtpPassword();
        if (StringUtils.hasText(incomingPwd) && !SMTP_PASSWORD_MASK.equals(incomingPwd)) {
            put(SettingKeys.SMTP_PASSWORD, incomingPwd);
        }
        put(SettingKeys.SMTP_FROM, req.getSmtpFrom());
        put(SettingKeys.SMTP_SSL_ENABLED, String.valueOf(req.isSmtpSslEnabled()));
    }

    @Override
    public boolean isRegisterEnabled() {
        return getBool(SettingKeys.REGISTER_ENABLED, true);
    }

    @Override
    public boolean isPasswordRegisterEnabled() {
        return getBool(SettingKeys.REGISTER_PASSWORD, true);
    }

    @Override
    public boolean isEmailRegisterEnabled() {
        return getBool(SettingKeys.REGISTER_EMAIL, false);
    }

    private int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return def;
        }
    }
}
