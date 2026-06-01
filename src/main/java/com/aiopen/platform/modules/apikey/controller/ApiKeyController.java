package com.aiopen.platform.modules.apikey.controller;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.modules.apikey.dto.CreateApiKeyRequest;
import com.aiopen.platform.modules.apikey.entity.ApiKey;
import com.aiopen.platform.modules.apikey.service.ApiKeyService;
import com.aiopen.platform.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /** 当前用户的所有 API Key */
    @GetMapping
    public Result<List<ApiKey>> list() {
        return Result.success(apiKeyService.listByUser(UserContext.getUserId()));
    }

    /** 创建 API Key */
    @PostMapping
    public Result<ApiKey> create(@Valid @RequestBody CreateApiKeyRequest request) {
        return Result.success(apiKeyService.createKey(UserContext.getUserId(), request));
    }

    /** 启用/禁用 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        requireOwned(id);
        ApiKey update = new ApiKey();
        update.setId(id);
        update.setStatus(status);
        apiKeyService.updateById(update);
        return Result.success();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireOwned(id);
        apiKeyService.removeById(id);
        return Result.success();
    }

    private ApiKey requireOwned(Long id) {
        ApiKey key = apiKeyService.getById(id);
        if (key == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!UserContext.isAdmin() && !key.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return key;
    }
}
