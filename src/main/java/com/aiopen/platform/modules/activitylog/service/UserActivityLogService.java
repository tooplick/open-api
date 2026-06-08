package com.aiopen.platform.modules.activitylog.service;

import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserActivityLogService extends IService<UserActivityLog> {

    /**
     * 记录一条用户活动日志。
     */
    void record(UserActivityLog log);
}
