package com.aiopen.platform.modules.user.service;

import com.aiopen.platform.modules.user.dto.LoginRequest;
import com.aiopen.platform.modules.user.dto.LoginResponse;

/**
 * 登录认证服务。独立于 {@link UserService},以避免
 * AuthenticationManager → UserDetailsService → UserService 形成循环依赖。
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);
}
