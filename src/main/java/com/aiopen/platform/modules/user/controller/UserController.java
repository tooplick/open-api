package com.aiopen.platform.modules.user.controller;

import com.aiopen.platform.common.result.PageResult;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.user.dto.ChangePasswordRequest;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.UserService;
import com.aiopen.platform.security.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<User> me() {
        return Result.success(userService.getById(UserContext.getUserId()));
    }

    /** 修改自己的密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(UserContext.getUserId(), request);
        return Result.success();
    }

    /** 管理员:分页查询用户 */
    @GetMapping("/page")
    public Result<PageResult<User>> page(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) String username) {
        UserContext.requireAdmin();
        Page<User> page = userService.page(new Page<>(current, size),
                Wrappers.<User>lambdaQuery()
                        .like(StringUtils.hasText(username), User::getUsername, username)
                        .orderByDesc(User::getId));
        return Result.success(PageResult.of(page));
    }

    /** 管理员:启用/禁用用户 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        UserContext.requireAdmin();
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userService.updateById(update);
        return Result.success();
    }
}
