package com.aiopen.platform.modules.user.controller;

import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.user.dto.LoginRequest;
import com.aiopen.platform.modules.user.dto.LoginResponse;
import com.aiopen.platform.modules.user.dto.RegisterRequest;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.AuthService;
import com.aiopen.platform.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}
