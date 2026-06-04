package com.aiopen.platform.modules.auth.email;

public interface EmailService {

    /** 向指定邮箱发送注册验证码;SMTP 未配置抛 EMAIL_SERVICE_NOT_CONFIGURED,发送失败抛 EMAIL_SEND_FAILED。 */
    void sendVerificationCode(String to, String code);
}
