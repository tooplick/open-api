package com.aiopen.platform.modules.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateApiKeyRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    /** 分组,决定可路由到哪些渠道 */
    private String group = "default";

    /** 模型白名单(逗号分隔),空表示不限制 */
    private String models;

    /** 过期时间,null 表示永不过期 */
    private LocalDateTime expireTime;
}
