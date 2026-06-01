package com.aiopen.platform.modules.user.service;

import com.aiopen.platform.modules.user.dto.ChangePasswordRequest;
import com.aiopen.platform.modules.user.dto.LoginRequest;
import com.aiopen.platform.modules.user.dto.LoginResponse;
import com.aiopen.platform.modules.user.dto.RegisterRequest;
import com.aiopen.platform.modules.user.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserService extends IService<User> {

    User register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    User getByUsername(String username);
}
