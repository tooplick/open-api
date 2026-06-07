package com.aiopen.platform.modules.setting.dto;

import lombok.Data;

/**
 * 管理员可见/可改的全部系统设置。GET 返回与 PUT 入参共用此结构(全量覆盖)。
 */
@Data
public class SettingsVO {

    private String siteName;
    private String siteSubtitle;
    private String siteFooter;
    private String loginAnnouncement;
    private String defaultKeyGroup;
    private String keyGroups;

    private boolean registerEnabled;
    private boolean passwordRegisterEnabled;
    private boolean emailRegisterEnabled;
    private boolean githubRegisterEnabled;
    private String githubClientId;
    private String githubClientSecret;
    private String githubRedirectUri;
    private String githubFrontendCallbackUri;

    private boolean proxyEnabled;
    private String proxyHost;
    private Integer proxyPort;

    // SMTP 邮件服务;密码读取时掩码回显,保存时留空或为掩码则保留原值
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private String smtpFrom;
    private boolean smtpSslEnabled;
}
