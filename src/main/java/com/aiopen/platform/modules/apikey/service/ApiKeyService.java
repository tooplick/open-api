package com.aiopen.platform.modules.apikey.service;

import com.aiopen.platform.modules.apikey.dto.CreateApiKeyRequest;
import com.aiopen.platform.modules.apikey.entity.ApiKey;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ApiKeyService extends IService<ApiKey> {

    ApiKey createKey(Long userId, CreateApiKeyRequest request);

    List<ApiKey> listByUser(Long userId);

    /** relay 鉴权用:按密钥明文查询 */
    ApiKey getByKeyValue(String key);
}
