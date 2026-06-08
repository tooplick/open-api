package com.aiopen.platform.modules.user.service.impl;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.aiopen.platform.modules.auth.email.EmailService;
import com.aiopen.platform.modules.auth.email.VerificationCodeStore;
import com.aiopen.platform.modules.setting.service.SystemSettingService;
import com.aiopen.platform.modules.user.dto.ChangePasswordRequest;
import com.aiopen.platform.modules.user.dto.EmailRegisterRequest;
import com.aiopen.platform.modules.user.dto.RegisterRequest;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.mapper.UserMapper;
import com.aiopen.platform.modules.user.service.UserService;
import com.aiopen.platform.security.Roles;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final SystemSettingService settingService;
    private final VerificationCodeStore verificationCodeStore;
    private final EmailService emailService;
    private final UserActivityLogService activityLogService;
    private final HttpServletRequest servletRequest;

    @Override
    public User register(RegisterRequest request) {
        if (!settingService.isRegisterEnabled() || !settingService.isPasswordRegisterEnabled()) {
            throw new BusinessException(ResultCode.REGISTER_DISABLED);
        }
        if (getByUsername(request.getUsername()) != null) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole(Roles.USER);
        user.setStatus(1);
        save(user);
        recordActivity(user.getId(), user.getUsername(), "REGISTER", "USER",
                user.getId(), user.getUsername(), "密码注册", 1);
        return user;
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(update);
        recordActivity(userId, user.getUsername(), "PASSWORD_CHANGE", "USER",
                userId, user.getUsername(), "修改密码", 1);
    }

    @Override
    public User getByUsername(String username) {
        return getOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username), false);
    }

    @Override
    public User getByEmail(String email) {
        return getOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email), false);
    }

    @Override
    public User getByGithubId(Long githubId) {
        if (githubId == null) {
            return null;
        }
        return getOne(Wrappers.<User>lambdaQuery().eq(User::getGithubId, githubId), false);
    }

    @Override
    public void sendRegisterEmailCode(String email) {
        if (!settingService.isRegisterEnabled() || !settingService.isEmailRegisterEnabled()) {
            throw new BusinessException(ResultCode.REGISTER_DISABLED);
        }
        if (getByEmail(email) != null) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }
        if (!verificationCodeStore.canSend(VerificationCodeStore.PURPOSE_REGISTER, email, Duration.ofSeconds(30))) {
            throw new BusinessException(ResultCode.EMAIL_CODE_TOO_FREQUENT);
        }
        String code = verificationCodeStore.generate(VerificationCodeStore.PURPOSE_REGISTER, email);
        emailService.sendVerificationCode(email, code);
        // 发送成功后才计入冷却,失败可立即重试
        verificationCodeStore.markSent(VerificationCodeStore.PURPOSE_REGISTER, email);
    }

    @Override
    @Transactional
    public User emailRegister(EmailRegisterRequest request) {
        if (!settingService.isRegisterEnabled() || !settingService.isEmailRegisterEnabled()) {
            throw new BusinessException(ResultCode.REGISTER_DISABLED);
        }
        if (!verificationCodeStore.verify(VerificationCodeStore.PURPOSE_REGISTER, request.getEmail(), request.getCode())) {
            throw new BusinessException(ResultCode.EMAIL_CODE_INVALID);
        }
        if (getByUsername(request.getUsername()) != null) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        if (getByEmail(request.getEmail()) != null) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole(Roles.USER);
        user.setStatus(1);
        save(user);
        recordActivity(user.getId(), user.getUsername(), "REGISTER_EMAIL", "USER",
                user.getId(), user.getUsername(), "邮箱验证码注册", 1);
        return user;
    }

    private void recordActivity(Long userId, String username, String action, String resourceType,
                                Long resourceId, String resourceName, String detail, int status) {
        UserActivityLog log = new UserActivityLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        log.setDetail(detail);
        log.setIp(getClientIp());
        log.setUserAgent(servletRequest.getHeader("User-Agent"));
        log.setStatus(status);
        activityLogService.record(log);
    }

    private String getClientIp() {
        String ip = servletRequest.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = servletRequest.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = servletRequest.getRemoteAddr();
        }
        return ip;
    }
}
