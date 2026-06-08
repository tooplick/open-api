package com.aiopen.platform.modules.user.controller;

import com.aiopen.platform.common.result.PageResult;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.aiopen.platform.modules.user.dto.ChangePasswordRequest;
import com.aiopen.platform.modules.user.dto.InitialCredentialsRequest;
import com.aiopen.platform.modules.user.dto.LoginResponse;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.AuthService;
import com.aiopen.platform.modules.user.service.UserService;
import com.aiopen.platform.security.AuthUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final UserActivityLogService activityLogService;
    private final HttpServletRequest servletRequest;

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<User> me(@AuthenticationPrincipal AuthUser principal) {
        return Result.success(userService.getById(principal.getId()));
    }

    /** 首次登录:强制修改初始账号与密码,返回新 token(因用户名变更旧 token 失效) */
    @PutMapping("/initial-credentials")
    public Result<LoginResponse> initialCredentials(@AuthenticationPrincipal AuthUser principal,
                                                    @Valid @RequestBody InitialCredentialsRequest request) {
        return Result.success(authService.changeInitialCredentials(principal.getId(), request));
    }

    /** 修改自己的密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal AuthUser principal,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
        return Result.success();
    }

    /** 管理员:分页查询用户 */
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<User>> page(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) String username) {
        Page<User> page = userService.page(new Page<>(current, size),
                Wrappers.<User>lambdaQuery()
                        .like(StringUtils.hasText(username), User::getUsername, username)
                        .orderByDesc(User::getId));
        return Result.success(PageResult.of(page));
    }

    /** 管理员:启用/禁用用户 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateStatus(@AuthenticationPrincipal AuthUser principal,
                                     @PathVariable Long id, @RequestParam Integer status) {
        User target = userService.getById(id);
        String targetName = target != null ? target.getUsername() : String.valueOf(id);
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userService.updateById(update);
        UserActivityLog log = new UserActivityLog();
        log.setUserId(principal.getId());
        log.setUsername(principal.getUsername());
        log.setAction("USER_STATUS_CHANGE");
        log.setResourceType("USER");
        log.setResourceId(id);
        log.setResourceName(targetName);
        log.setDetail("状态变更为 " + (status == 1 ? "启用" : "禁用"));
        log.setIp(servletRequest.getRemoteAddr());
        log.setUserAgent(servletRequest.getHeader("User-Agent"));
        log.setStatus(1);
        activityLogService.record(log);
        return Result.success();
    }
}
