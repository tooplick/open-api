package com.aiopen.platform.modules.auth.email.impl;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.modules.auth.email.EmailService;
import com.aiopen.platform.modules.setting.SettingKeys;
import com.aiopen.platform.modules.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * 从系统设置(system_setting)读取 SMTP 配置,运行时构造 {@link JavaMailSenderImpl} 发信,
 * 因此配置改动即时生效、无需重启。每次发信新建发信器(低频场景,不缓存)。
 */
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final SystemSettingService settingService;

    @Override
    public void sendVerificationCode(String to, String code) {
        String host = settingService.get(SettingKeys.SMTP_HOST, "");
        String username = settingService.get(SettingKeys.SMTP_USERNAME, "");
        // 注意:读密码须用底层 get 取明文,不能用 getSettings()(那是掩码版)
        String password = settingService.get(SettingKeys.SMTP_PASSWORD, "");
        if (!StringUtils.hasText(host) || !StringUtils.hasText(username)) {
            throw new BusinessException(ResultCode.EMAIL_SERVICE_NOT_CONFIGURED);
        }
        int port = parsePort(settingService.get(SettingKeys.SMTP_PORT, "587"));
        boolean ssl = settingService.getBool(SettingKeys.SMTP_SSL_ENABLED, false);
        String from = settingService.get(SettingKeys.SMTP_FROM, "");
        if (!StringUtils.hasText(from)) {
            from = username;
        }
        String siteName = settingService.get(SettingKeys.SITE_NAME, "AI Open Platform");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(siteName + " 注册验证码");
        message.setText("您的注册验证码是:" + code + "\n\n验证码 10 分钟内有效,请勿泄露给他人。");
        try {
            sender.send(message);
        } catch (MailException e) {
            throw new BusinessException(ResultCode.EMAIL_SEND_FAILED);
        }
    }

    private int parsePort(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 587;
        }
    }
}
