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
}
