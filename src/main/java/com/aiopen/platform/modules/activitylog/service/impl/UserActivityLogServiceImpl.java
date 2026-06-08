package com.aiopen.platform.modules.activitylog.service.impl;

import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.mapper.UserActivityLogMapper;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserActivityLogServiceImpl extends ServiceImpl<UserActivityLogMapper, UserActivityLog>
        implements UserActivityLogService {

    @Override
    public void record(UserActivityLog log) {
        save(log);
    }
}
