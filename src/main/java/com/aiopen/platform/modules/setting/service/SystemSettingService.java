package com.aiopen.platform.modules.setting.service;

import com.aiopen.platform.modules.setting.dto.PublicSettingsVO;
import com.aiopen.platform.modules.setting.dto.SettingsVO;
import com.aiopen.platform.modules.setting.entity.SystemSetting;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SystemSettingService extends IService<SystemSetting> {

    /** 读取字符串设置;不存在返回 def */
    String get(String key, String def);

    /** 读取布尔设置;不存在返回 def */
    boolean getBool(String key, boolean def);

    /** 启动时确保默认项存在(不覆盖已有值) */
    void ensureDefaults();

    /** 管理员:全部设置 */
    SettingsVO getSettings();

    /** 公开:登录页所需子集 */
    PublicSettingsVO getPublicSettings();

    /** 管理员:全量更新设置 */
    void updateSettings(SettingsVO req);

    /** 是否开放注册(总开关) */
    boolean isRegisterEnabled();

    /** 是否开放账号密码注册 */
    boolean isPasswordRegisterEnabled();
}
