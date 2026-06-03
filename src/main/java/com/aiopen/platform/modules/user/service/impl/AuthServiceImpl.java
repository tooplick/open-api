package com.aiopen.platform.modules.user.service.impl;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.modules.user.dto.InitialCredentialsRequest;
import com.aiopen.platform.modules.user.dto.LoginRequest;
import com.aiopen.platform.modules.user.dto.LoginResponse;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.AuthService;
import com.aiopen.platform.modules.user.service.UserService;
import com.aiopen.platform.security.AuthUser;
import com.aiopen.platform.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录认证经 Spring Security 的 {@link AuthenticationManager} 完成
 * (底层 DaoAuthenticationProvider + CustomUserDetailsService + PasswordEncoder),
 * 认证成功后签发 JWT。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (DisabledException e) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        } catch (AuthenticationException e) {
            // 用户名不存在(默认被隐藏为 BadCredentials)与密码错误统一返回同一提示
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        AuthUser principal = (AuthUser) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal.getId(), principal.getUsername(), principal.getRole());
        User user = userService.getById(principal.getId());
        return new LoginResponse(token, user);
    }

    @Override
    public LoginResponse changeInitialCredentials(Long userId, InitialCredentialsRequest request) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getMustChangePassword() == null || user.getMustChangePassword() != 1) {
            // 非首登强制态,禁止借此接口随意改用户名
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (!user.getUsername().equals(request.getUsername())
                && userService.getByUsername(request.getUsername()) != null) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        User update = new User();
        update.setId(userId);
        update.setUsername(request.getUsername());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        update.setMustChangePassword(0);
        userService.updateById(update);

        User fresh = userService.getById(userId);
        // 用户名是 JWT 的一部分(过滤器按用户名加载),改名后旧 token 失效,这里重新签发
        String token = jwtUtil.generateToken(fresh.getId(), fresh.getUsername(), fresh.getRole());
        return new LoginResponse(token, fresh);
    }
}
