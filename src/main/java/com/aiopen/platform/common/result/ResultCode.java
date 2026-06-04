package com.aiopen.platform.common.result;

import lombok.Getter;

/**
 * 统一返回状态码。
 * 1xx 段为通用 HTTP 语义,1xxx 段为业务自定义错误。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "资源不存在"),
    ERROR(500, "服务器内部错误"),

    // 用户相关 10xx
    USERNAME_EXISTS(1001, "用户名已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),
    ACCOUNT_DISABLED(1004, "账号已被禁用"),
    OLD_PASSWORD_ERROR(1005, "原密码不正确"),
    REGISTER_DISABLED(1006, "当前未开放该注册方式"),
    EMAIL_EXISTS(1007, "该邮箱已被注册"),
    EMAIL_CODE_INVALID(1008, "验证码错误或已过期"),
    EMAIL_CODE_TOO_FREQUENT(1009, "验证码发送过于频繁，请稍后再试"),
    EMAIL_SERVICE_NOT_CONFIGURED(1010, "邮件服务未配置，请联系管理员"),
    EMAIL_SEND_FAILED(1011, "邮件发送失败，请稍后重试"),

    // 额度相关 11xx
    QUOTA_INSUFFICIENT(1101, "额度不足"),

    // API Key 相关 12xx
    APIKEY_INVALID(1201, "API Key 无效"),
    APIKEY_DISABLED(1202, "API Key 已被禁用"),
    APIKEY_EXPIRED(1203, "API Key 已过期"),

    // 渠道/模型相关 13xx
    NO_CHANNEL_AVAILABLE(1301, "没有可用的渠道支持该模型"),
    CHANNEL_REQUEST_FAILED(1302, "上游渠道请求失败"),
    MODEL_NOT_SUPPORTED(1303, "暂不支持该模型");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
