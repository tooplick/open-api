package com.aiopen.platform.modules.apikey.service.impl;

import com.aiopen.platform.modules.apikey.dto.CreateApiKeyRequest;
import com.aiopen.platform.modules.apikey.entity.ApiKey;
import com.aiopen.platform.modules.apikey.mapper.ApiKeyMapper;
import com.aiopen.platform.modules.apikey.service.ApiKeyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> implements ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int KEY_LENGTH = 48;

    @Override
    public ApiKey createKey(Long userId, CreateApiKeyRequest request) {
        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setName(request.getName());
        apiKey.setApiKey(generateUniqueKey());
        apiKey.setStatus(1);
        apiKey.setQuota(request.getQuota() == null ? 0L : request.getQuota());
        apiKey.setUsedQuota(0L);
        apiKey.setExpireTime(request.getExpireTime());
        save(apiKey);
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

    @Override
    public void addUsedQuota(Long id, long delta) {
        if (delta == 0) {
            return;
        }
        update(Wrappers.<ApiKey>lambdaUpdate()
                .setSql("used_quota = used_quota + " + delta)
                .eq(ApiKey::getId, id));
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
}
