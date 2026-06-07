package com.aiopen.platform.modules.user.controller;

import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.auth.github.GithubOAuthService;
import com.aiopen.platform.modules.user.dto.GithubAuthorizeResponse;
import com.aiopen.platform.modules.user.dto.GithubTicketExchangeRequest;
import com.aiopen.platform.modules.user.dto.EmailRegisterRequest;
import com.aiopen.platform.modules.user.dto.LoginRequest;
import com.aiopen.platform.modules.user.dto.LoginResponse;
import com.aiopen.platform.modules.user.dto.RegisterRequest;
import com.aiopen.platform.modules.user.dto.SendEmailCodeRequest;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.AuthService;
import com.aiopen.platform.modules.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final GithubOAuthService githubOAuthService;

    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/email-code")
    public Result<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        userService.sendRegisterEmailCode(request.getEmail());
        return Result.success();
    }

    @PostMapping("/email-register")
    public Result<User> emailRegister(@Valid @RequestBody EmailRegisterRequest request) {
        return Result.success(userService.emailRegister(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/github/authorize-url")
    public Result<GithubAuthorizeResponse> githubAuthorizeUrl(
            @RequestParam(required = false) String redirect,
            HttpServletRequest request) {
        return Result.success(githubOAuthService.buildAuthorizeUrl(redirect, request));
    }

    @GetMapping("/github/callback")
    public void githubCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        response.sendRedirect(githubOAuthService.handleCallback(code, state, error, errorDescription, request));
    }

    @PostMapping("/github/exchange")
    public Result<LoginResponse> githubExchange(@Valid @RequestBody GithubTicketExchangeRequest request) {
        return Result.success(githubOAuthService.exchangeTicket(request.getTicket()));
    }
}
