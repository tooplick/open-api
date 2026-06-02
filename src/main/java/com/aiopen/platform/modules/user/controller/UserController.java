package com.aiopen.platform.modules.user.controller;

import com.aiopen.platform.common.result.PageResult;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.user.dto.ChangePasswordRequest;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.UserService;
import com.aiopen.platform.security.AuthUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<User> me(@AuthenticationPrincipal AuthUser principal) {
        return Result.success(userService.getById(principal.getId()));
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
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userService.updateById(update);
        return Result.success();
    }
}
