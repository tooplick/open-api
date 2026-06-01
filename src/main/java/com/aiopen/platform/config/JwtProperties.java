package com.aiopen.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 相关配置,对应 application.yml 中 ai-open.jwt.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-open.jwt")
public class JwtProperties {

    /** 签名密钥(HMAC-SHA),长度需 >= 32 字节 */
    private String secret;

    /** 过期时间(毫秒) */
    private long expire;

    /** 请求头名称 */
    private String header = "Authorization";

    /** token 前缀 */
    private String prefix = "Bearer ";
}
