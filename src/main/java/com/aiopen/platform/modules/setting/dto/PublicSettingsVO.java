package com.aiopen.platform.modules.setting.dto;

import lombok.Data;

/**
 * 登录页等无需鉴权即可读取的公开设置子集。
 */
@Data
public class PublicSettingsVO {

    private String siteName;
    private String siteSubtitle;
    private String loginAnnouncement;
    private String defaultKeyGroup;

    private boolean registerEnabled;
    private boolean passwordRegisterEnabled;
    private boolean emailRegisterEnabled;
    private boolean githubRegisterEnabled;
}
