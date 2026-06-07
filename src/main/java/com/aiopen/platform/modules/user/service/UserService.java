package com.aiopen.platform.modules.user.service;

import com.aiopen.platform.modules.user.dto.ChangePasswordRequest;
import com.aiopen.platform.modules.user.dto.EmailRegisterRequest;
import com.aiopen.platform.modules.user.dto.RegisterRequest;
import com.aiopen.platform.modules.user.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserService extends IService<User> {

    User register(RegisterRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    User getByUsername(String username);

    User getByEmail(String email);

    User getByGithubId(Long githubId);

    /** 发送注册邮箱验证码(含门控、邮箱查重、发送冷却) */
    void sendRegisterEmailCode(String email);

    /** 邮箱验证码注册(校验验证码 + 用户名/邮箱查重 + 建号) */
    User emailRegister(EmailRegisterRequest request);
}
