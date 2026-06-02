package com.aiopen.platform.modules.apikey.controller;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.modules.apikey.dto.CreateApiKeyRequest;
import com.aiopen.platform.modules.apikey.entity.ApiKey;
import com.aiopen.platform.modules.apikey.service.ApiKeyService;
import com.aiopen.platform.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /** 当前用户的所有 API Key */
    @GetMapping
    public Result<List<ApiKey>> list(@AuthenticationPrincipal AuthUser principal) {
        return Result.success(apiKeyService.listByUser(principal.getId()));
    }

    /** 创建 API Key */
    @PostMapping
    public Result<ApiKey> create(@AuthenticationPrincipal AuthUser principal,
                                 @Valid @RequestBody CreateApiKeyRequest request) {
        return Result.success(apiKeyService.createKey(principal.getId(), request));
    }

    /** 启用/禁用 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@AuthenticationPrincipal AuthUser principal,
                                     @PathVariable Long id, @RequestParam Integer status) {
        requireOwned(id, principal);
        ApiKey update = new ApiKey();
        update.setId(id);
        update.setStatus(status);
        apiKeyService.updateById(update);
        return Result.success();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal AuthUser principal, @PathVariable Long id) {
        requireOwned(id, principal);
        apiKeyService.removeById(id);
        return Result.success();
    }

    private ApiKey requireOwned(Long id, AuthUser principal) {
        ApiKey key = apiKeyService.getById(id);
        if (key == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!principal.isAdmin() && !key.getUserId().equals(principal.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return key;
    }
}
