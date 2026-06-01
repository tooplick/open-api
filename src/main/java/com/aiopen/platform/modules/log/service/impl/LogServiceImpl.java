package com.aiopen.platform.modules.log.service.impl;

import com.aiopen.platform.modules.log.dto.LogStatVO;
import com.aiopen.platform.modules.log.entity.Log;
import com.aiopen.platform.modules.log.mapper.LogMapper;
import com.aiopen.platform.modules.log.service.LogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class LogServiceImpl extends ServiceImpl<LogMapper, Log> implements LogService {

    @Override
    public void record(Log log) {
        save(log);
    }

    @Override
    public LogStatVO statistics(Long userId, LocalDateTime start, LocalDateTime end) {
        QueryWrapper<Log> qw = new QueryWrapper<>();
        qw.select("COUNT(*) AS requests",
                "IFNULL(SUM(prompt_tokens),0) AS promptTokens",
                "IFNULL(SUM(completion_tokens),0) AS completionTokens",
                "IFNULL(SUM(total_tokens),0) AS totalTokens",
                "IFNULL(SUM(quota),0) AS quota");
        if (userId != null) {
            qw.eq("user_id", userId);
        }
        if (start != null) {
            qw.ge("create_time", start);
        }
        if (end != null) {
            qw.le("create_time", end);
        }
        List<Map<String, Object>> rows = listMaps(qw);
        LogStatVO vo = new LogStatVO();
        if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
            Map<String, Object> row = rows.get(0);
            vo.setRequests(toLong(row.get("requests")));
            vo.setPromptTokens(toLong(row.get("promptTokens")));
            vo.setCompletionTokens(toLong(row.get("completionTokens")));
            vo.setTotalTokens(toLong(row.get("totalTokens")));
            vo.setQuota(toLong(row.get("quota")));
        }
        return vo;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
