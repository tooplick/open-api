package com.aiopen.platform.modules.user.service.impl;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
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
}
