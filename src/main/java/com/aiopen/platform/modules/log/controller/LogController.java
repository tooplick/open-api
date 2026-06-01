package com.aiopen.platform.modules.log.controller;

import com.aiopen.platform.common.result.PageResult;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.log.dto.LogStatVO;
import com.aiopen.platform.modules.log.entity.Log;
import com.aiopen.platform.modules.log.service.LogService;
import com.aiopen.platform.security.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 调用日志查询。普通用户仅能查看自己的日志,管理员可查看全部并按 userId 过滤。
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping("/page")
    public Result<PageResult<Log>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) Long userId,
                                        @RequestParam(required = false) Long channelId,
                                        @RequestParam(required = false) String modelName,
                                        @RequestParam(required = false) Integer type,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Long effectiveUserId = UserContext.isAdmin() ? userId : UserContext.getUserId();
        Page<Log> page = logService.page(new Page<>(current, size),
                Wrappers.<Log>lambdaQuery()
                        .eq(effectiveUserId != null, Log::getUserId, effectiveUserId)
                        .eq(channelId != null, Log::getChannelId, channelId)
                        .eq(type != null, Log::getType, type)
                        .like(StringUtils.hasText(modelName), Log::getModelName, modelName)
                        .ge(startTime != null, Log::getCreateTime, startTime)
                        .le(endTime != null, Log::getCreateTime, endTime)
                        .orderByDesc(Log::getId));
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/statistics")
    public Result<LogStatVO> statistics(@RequestParam(required = false) Long userId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Long effectiveUserId = UserContext.isAdmin() ? userId : UserContext.getUserId();
        return Result.success(logService.statistics(effectiveUserId, startTime, endTime));
    }
}
