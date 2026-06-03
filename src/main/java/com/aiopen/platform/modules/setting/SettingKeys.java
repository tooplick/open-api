package com.aiopen.platform.modules.setting;

/**
 * 系统设置键常量。值统一以字符串存储于 system_setting 表。
 */
public final class SettingKeys {

    private SettingKeys() {
    }

    public static final String SITE_NAME = "site.name";
    public static final String SITE_SUBTITLE = "site.subtitle";
    public static final String SITE_FOOTER = "site.footer";
    public static final String LOGIN_ANNOUNCEMENT = "login.announcement";
    public static final String DEFAULT_KEY_GROUP = "key.default_group";

    /** 注册总开关 */
    public static final String REGISTER_ENABLED = "register.enabled";
    /** 账号密码注册 */
    public static final String REGISTER_PASSWORD = "register.password.enabled";
    /** 邮箱验证码注册(实现见后续阶段) */
    public static final String REGISTER_EMAIL = "register.email.enabled";
    /** GitHub OAuth 注册(实现见后续阶段) */
    public static final String REGISTER_GITHUB = "register.github.enabled";
}
