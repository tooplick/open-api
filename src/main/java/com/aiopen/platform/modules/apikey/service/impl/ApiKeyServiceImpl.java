package com.aiopen.platform.modules.apikey.service.impl;

import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.aiopen.platform.modules.apikey.dto.CreateApiKeyRequest;
import com.aiopen.platform.modules.apikey.entity.ApiKey;
import com.aiopen.platform.modules.apikey.mapper.ApiKeyMapper;
import com.aiopen.platform.modules.apikey.service.ApiKeyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> implements ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int KEY_LENGTH = 48;

    @Lazy
    private final UserActivityLogService activityLogService;
    private final HttpServletRequest servletRequest;

    @Override
    public ApiKey createKey(Long userId, CreateApiKeyRequest request) {
        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setName(request.getName());
        apiKey.setApiKey(generateUniqueKey());
        apiKey.setStatus(1);
        apiKey.setGroup(StringUtils.hasText(request.getGroup()) ? request.getGroup().trim() : "default");
        apiKey.setModels(request.getModels());
        apiKey.setExpireTime(request.getExpireTime());
        save(apiKey);
        recordActivity(userId, "APIKEY_CREATE", "API_KEY", apiKey.getId(),
                request.getName(), "创建 API Key", 1);
        return apiKey;
    }

    @Override
    public List<ApiKey> listByUser(Long userId) {
        return list(Wrappers.<ApiKey>lambdaQuery()
                .eq(ApiKey::getUserId, userId)
                .orderByDesc(ApiKey::getId));
    }

    @Override
    public ApiKey getByKeyValue(String key) {
        return getOne(Wrappers.<ApiKey>lambdaQuery().eq(ApiKey::getApiKey, key), false);
    }

    private String generateUniqueKey() {
        String key;
        do {
            StringBuilder sb = new StringBuilder("sk-");
            for (int i = 0; i < KEY_LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            key = sb.toString();
        } while (getByKeyValue(key) != null);
        return key;
    }

    private void recordActivity(Long userId, String action, String resourceType,
                                Long resourceId, String resourceName, String detail, int status) {
        UserActivityLog log = new UserActivityLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        log.setDetail(detail);
        log.setIp(servletRequest.getRemoteAddr());
        log.setUserAgent(servletRequest.getHeader("User-Agent"));
        log.setStatus(status);
        activityLogService.record(log);
    }
}
