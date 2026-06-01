package com.aiopen.platform.modules.relay;

import com.aiopen.platform.modules.apikey.entity.ApiKey;
import com.aiopen.platform.modules.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * relay 鉴权通过后的上下文。
 */
@Data
@AllArgsConstructor
public class RelayContext {

    private ApiKey apiKey;
    private User user;
    /** 分组(取自 apiKey.group),决定可路由到哪些渠道 */
    private String group;
    /** 模型白名单(取自 apiKey.models,逗号分隔),空表示不限制 */
    private String modelLimits;
}
