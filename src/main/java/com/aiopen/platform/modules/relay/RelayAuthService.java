package com.aiopen.platform.modules.relay;

import com.aiopen.platform.modules.apikey.entity.ApiKey;
import com.aiopen.platform.modules.apikey.service.ApiKeyService;
import com.aiopen.platform.modules.relay.exception.RelayException;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * relay 的 API Key 鉴权与额度预检。
 */
@Service
@RequiredArgsConstructor
public class RelayAuthService {

    private static final String BEARER = "Bearer ";

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    public RelayContext authenticate(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER)) {
            throw new RelayException(401, "invalid_request_error", "缺少有效的 Authorization Bearer 令牌");
        }
        String key = authorizationHeader.substring(BEARER.length()).trim();
        ApiKey apiKey = apiKeyService.getByKeyValue(key);
        if (apiKey == null) {
            throw new RelayException(401, "invalid_api_key", "API Key 无效");
        }
        if (apiKey.getStatus() == null || apiKey.getStatus() != 1) {
            throw new RelayException(403, "api_key_disabled", "API Key 已被禁用");
        }
        if (apiKey.getExpireTime() != null && apiKey.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RelayException(403, "api_key_expired", "API Key 已过期");
        }
        User user = userService.getById(apiKey.getUserId());
        if (user == null) {
            throw new RelayException(401, "invalid_api_key", "API Key 对应用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RelayException(403, "account_disabled", "账号已被禁用");
        }
        if (exceeded(user.getQuota(), user.getUsedQuota())) {
            throw new RelayException(429, "insufficient_quota", "用户额度不足");
        }
        if (exceeded(apiKey.getQuota(), apiKey.getUsedQuota())) {
            throw new RelayException(429, "insufficient_quota", "API Key 额度不足");
        }
        return new RelayContext(apiKey, user);
    }

    private boolean exceeded(Long quota, Long used) {
        long q = quota == null ? 0 : quota;
        long u = used == null ? 0 : used;
        return q > 0 && u >= q;
    }
}
