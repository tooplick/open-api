package com.aiopen.platform.modules.activitylog.controller;

import com.aiopen.platform.common.result.PageResult;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.aiopen.platform.security.AuthUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 用户活动日志查询。普通用户仅能查看自己的日志,管理员可查看全部并按 userId 过滤。
 */
@RestController
@RequestMapping("/api/activity-logs")
@RequiredArgsConstructor
public class UserActivityLogController {

    private final UserActivityLogService activityLogService;

    @GetMapping("/page")
    public Result<PageResult<UserActivityLog>> page(@AuthenticationPrincipal AuthUser principal,
                                                    @RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) String resourceType,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Long effectiveUserId = principal.isAdmin() ? userId : principal.getId();
        Page<UserActivityLog> page = activityLogService.page(new Page<>(current, size),
                Wrappers.<UserActivityLog>lambdaQuery()
                        .eq(effectiveUserId != null, UserActivityLog::getUserId, effectiveUserId)
                        .eq(StringUtils.hasText(action), UserActivityLog::getAction, action)
                        .eq(StringUtils.hasText(resourceType), UserActivityLog::getResourceType, resourceType)
                        .ge(startTime != null, UserActivityLog::getCreateTime, startTime)
                        .le(endTime != null, UserActivityLog::getCreateTime, endTime)
                        .orderByDesc(UserActivityLog::getId));
        return Result.success(PageResult.of(page));
    }
}
