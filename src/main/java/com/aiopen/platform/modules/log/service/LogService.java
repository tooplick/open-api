package com.aiopen.platform.modules.log.service;

import com.aiopen.platform.modules.log.dto.LogDailyStatVO;
import com.aiopen.platform.modules.log.dto.LogStatVO;
import com.aiopen.platform.modules.log.entity.Log;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;

public interface LogService extends IService<Log> {

    void record(Log log);

    /** 用量统计;userId 为 null 表示统计全部(管理员) */
    LogStatVO statistics(Long userId, LocalDateTime start, LocalDateTime end);

    /** 按天聚合用量(group by date),用于趋势图;userId 为 null 表示全部 */
    List<LogDailyStatVO> dailyStatistics(Long userId, LocalDateTime start, LocalDateTime end);
}
