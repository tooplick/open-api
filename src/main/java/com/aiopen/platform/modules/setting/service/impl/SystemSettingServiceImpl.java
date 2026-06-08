package com.aiopen.platform.modules.setting.service.impl;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.aiopen.platform.modules.setting.SettingKeys;
import com.aiopen.platform.modules.setting.dto.PublicSettingsVO;
import com.aiopen.platform.modules.setting.dto.SettingsVO;
import com.aiopen.platform.modules.setting.entity.SystemSetting;
import com.aiopen.platform.modules.setting.mapper.SystemSettingMapper;
import com.aiopen.platform.modules.setting.service.SystemSettingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl
        extends ServiceImpl<SystemSettingMapper, SystemSetting>
        implements SystemSettingService {

    @Lazy
    private final UserActivityLogService activityLogService;
    private final HttpServletRequest servletRequest;

    private static final String DEFAULT_GROUP = "default";
    private static final String SMTP_PASSWORD_MASK = "******";
    private static final String GITHUB_CLIENT_SECRET_MASK = "******";

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
        putIfAbsent(SettingKeys.KEY_GROUPS, "default");
        putIfAbsent(SettingKeys.REGISTER_ENABLED, "true");
        putIfAbsent(SettingKeys.REGISTER_PASSWORD, "true");
        putIfAbsent(SettingKeys.REGISTER_EMAIL, "false");
        putIfAbsent(SettingKeys.REGISTER_GITHUB, "false");
        putIfAbsent(SettingKeys.GITHUB_CLIENT_ID, "");
        putIfAbsent(SettingKeys.GITHUB_CLIENT_SECRET, "");
        putIfAbsent(SettingKeys.GITHUB_REDIRECT_URI, "");
        putIfAbsent(SettingKeys.GITHUB_FRONTEND_CALLBACK_URI, "");
        putIfAbsent(SettingKeys.PROXY_ENABLED, "false");
        putIfAbsent(SettingKeys.PROXY_HOST, "");
        putIfAbsent(SettingKeys.PROXY_PORT, "7890");
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
        KeyGroupConfig keyGroupConfig = normalizeKeyGroups(
                get(SettingKeys.KEY_GROUPS, DEFAULT_GROUP),
                get(SettingKeys.DEFAULT_KEY_GROUP, DEFAULT_GROUP),
                false);
        vo.setSiteName(get(SettingKeys.SITE_NAME, "AI Open Platform"));
        vo.setSiteSubtitle(get(SettingKeys.SITE_SUBTITLE, "大模型聚合开放平台"));
        vo.setSiteFooter(get(SettingKeys.SITE_FOOTER, "AI 模型聚合开放平台"));
        vo.setLoginAnnouncement(get(SettingKeys.LOGIN_ANNOUNCEMENT, ""));
        vo.setDefaultKeyGroup(keyGroupConfig.defaultGroup());
        vo.setKeyGroups(keyGroupConfig.groups());
        vo.setRegisterEnabled(getBool(SettingKeys.REGISTER_ENABLED, true));
        vo.setPasswordRegisterEnabled(getBool(SettingKeys.REGISTER_PASSWORD, true));
        vo.setEmailRegisterEnabled(getBool(SettingKeys.REGISTER_EMAIL, false));
        vo.setGithubRegisterEnabled(getBool(SettingKeys.REGISTER_GITHUB, false));
        vo.setGithubClientId(get(SettingKeys.GITHUB_CLIENT_ID, ""));
        String githubSecret = get(SettingKeys.GITHUB_CLIENT_SECRET, "");
        vo.setGithubClientSecret(StringUtils.hasText(githubSecret) ? GITHUB_CLIENT_SECRET_MASK : "");
        vo.setGithubRedirectUri(get(SettingKeys.GITHUB_REDIRECT_URI, ""));
        vo.setGithubFrontendCallbackUri(get(SettingKeys.GITHUB_FRONTEND_CALLBACK_URI, ""));
        vo.setProxyEnabled(getBool(SettingKeys.PROXY_ENABLED, false));
        vo.setProxyHost(get(SettingKeys.PROXY_HOST, ""));
        vo.setProxyPort(parseInt(get(SettingKeys.PROXY_PORT, "7890"), 7890));
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
        KeyGroupConfig keyGroupConfig = normalizeKeyGroups(
                get(SettingKeys.KEY_GROUPS, DEFAULT_GROUP),
                get(SettingKeys.DEFAULT_KEY_GROUP, DEFAULT_GROUP),
                false);
        vo.setSiteName(get(SettingKeys.SITE_NAME, "AI Open Platform"));
        vo.setSiteSubtitle(get(SettingKeys.SITE_SUBTITLE, "大模型聚合开放平台"));
        vo.setLoginAnnouncement(get(SettingKeys.LOGIN_ANNOUNCEMENT, ""));
        vo.setDefaultKeyGroup(keyGroupConfig.defaultGroup());
        vo.setKeyGroups(keyGroupConfig.groups());
        vo.setRegisterEnabled(getBool(SettingKeys.REGISTER_ENABLED, true));
        vo.setPasswordRegisterEnabled(getBool(SettingKeys.REGISTER_PASSWORD, true));
        vo.setEmailRegisterEnabled(getBool(SettingKeys.REGISTER_EMAIL, false));
        vo.setGithubRegisterEnabled(getBool(SettingKeys.REGISTER_GITHUB, false));
        return vo;
    }

    @Override
    @Transactional
    public void updateSettings(SettingsVO req) {
        KeyGroupConfig keyGroupConfig = normalizeKeyGroups(req.getKeyGroups(), req.getDefaultKeyGroup(), true);
        put(SettingKeys.SITE_NAME, req.getSiteName());
        put(SettingKeys.SITE_SUBTITLE, req.getSiteSubtitle());
        put(SettingKeys.SITE_FOOTER, req.getSiteFooter());
        put(SettingKeys.LOGIN_ANNOUNCEMENT, req.getLoginAnnouncement());
        put(SettingKeys.DEFAULT_KEY_GROUP, keyGroupConfig.defaultGroup());
        put(SettingKeys.KEY_GROUPS, keyGroupConfig.groups());
        put(SettingKeys.REGISTER_ENABLED, String.valueOf(req.isRegisterEnabled()));
        put(SettingKeys.REGISTER_PASSWORD, String.valueOf(req.isPasswordRegisterEnabled()));
        put(SettingKeys.REGISTER_EMAIL, String.valueOf(req.isEmailRegisterEnabled()));
        put(SettingKeys.REGISTER_GITHUB, String.valueOf(req.isGithubRegisterEnabled()));
        put(SettingKeys.GITHUB_CLIENT_ID, req.getGithubClientId());
        String incomingGithubSecret = req.getGithubClientSecret();
        if (StringUtils.hasText(incomingGithubSecret) && !GITHUB_CLIENT_SECRET_MASK.equals(incomingGithubSecret)) {
            put(SettingKeys.GITHUB_CLIENT_SECRET, incomingGithubSecret);
        }
        put(SettingKeys.GITHUB_REDIRECT_URI, req.getGithubRedirectUri());
        put(SettingKeys.GITHUB_FRONTEND_CALLBACK_URI, req.getGithubFrontendCallbackUri());
        put(SettingKeys.PROXY_ENABLED, String.valueOf(req.isProxyEnabled()));
        put(SettingKeys.PROXY_HOST, req.getProxyHost());
        put(SettingKeys.PROXY_PORT, String.valueOf(req.getProxyPort() == null ? 7890 : req.getProxyPort()));
        put(SettingKeys.SMTP_HOST, req.getSmtpHost());
        put(SettingKeys.SMTP_PORT, String.valueOf(req.getSmtpPort() == null ? 587 : req.getSmtpPort()));
        put(SettingKeys.SMTP_USERNAME, req.getSmtpUsername());
        String incomingPwd = req.getSmtpPassword();
        if (StringUtils.hasText(incomingPwd) && !SMTP_PASSWORD_MASK.equals(incomingPwd)) {
            put(SettingKeys.SMTP_PASSWORD, incomingPwd);
        }
        put(SettingKeys.SMTP_FROM, req.getSmtpFrom());
        put(SettingKeys.SMTP_SSL_ENABLED, String.valueOf(req.isSmtpSslEnabled()));
        recordActivity("SETTING_UPDATE", "SETTING", null, null, "更新系统设置", 1);
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

    @Override
    public boolean isGithubRegisterEnabled() {
        return getBool(SettingKeys.REGISTER_GITHUB, false);
    }

    private int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return def;
        }
    }

    private KeyGroupConfig normalizeKeyGroups(String rawGroups, String rawDefaultGroup, boolean strict) {
        List<String> groups = parseKeyGroups(rawGroups);
        if (groups.isEmpty()) {
            if (strict) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "At least one key group is required");
            }
            groups.add(DEFAULT_GROUP);
        }

        String defaultGroup = StringUtils.hasText(rawDefaultGroup) ? rawDefaultGroup.trim() : groups.get(0);
        if (!groups.contains(defaultGroup)) {
            if (strict) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Default key group must be in key groups");
            }
            defaultGroup = groups.get(0);
        }
        return new KeyGroupConfig(defaultGroup, String.join(",", groups));
    }

    private List<String> parseKeyGroups(String rawGroups) {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        if (StringUtils.hasText(rawGroups)) {
            for (String part : rawGroups.split(",")) {
                String group = part.trim();
                if (StringUtils.hasText(group)) {
                    groups.add(group);
                }
            }
        }
        return new ArrayList<>(groups);
    }

    private record KeyGroupConfig(String defaultGroup, String groups) {
    }

    private void recordActivity(String action, String resourceType, Long resourceId,
                                String resourceName, String detail, int status) {
        UserActivityLog log = new UserActivityLog();
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        log.setDetail(detail);
        log.setIp(servletRequest.getRemoteAddr());
        log.setUserAgent(servletRequest.getHeader("User-Agent"));
        log.setStatus(status);
        activityLogService.record(log);
    }
}
