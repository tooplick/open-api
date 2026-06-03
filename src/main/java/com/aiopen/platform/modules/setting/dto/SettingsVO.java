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

    private boolean registerEnabled;
    private boolean passwordRegisterEnabled;
    private boolean emailRegisterEnabled;
    private boolean githubRegisterEnabled;
}
