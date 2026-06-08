package com.aiopen.platform.modules.apikey.controller;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.aiopen.platform.modules.apikey.dto.CreateApiKeyRequest;
import com.aiopen.platform.modules.apikey.entity.ApiKey;
import com.aiopen.platform.modules.apikey.service.ApiKeyService;
import com.aiopen.platform.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
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
    private final UserActivityLogService activityLogService;
    private final HttpServletRequest servletRequest;

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
        ApiKey key = requireOwned(id, principal);
        ApiKey update = new ApiKey();
        update.setId(id);
        update.setStatus(status);
        apiKeyService.updateById(update);
        recordActivity(principal.getId(), principal.getUsername(), "APIKEY_STATUS_CHANGE", "API_KEY",
                id, key.getName(), "状态变更为 " + (status == 1 ? "启用" : "禁用"), 1);
        return Result.success();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal AuthUser principal, @PathVariable Long id) {
        ApiKey key = requireOwned(id, principal);
        apiKeyService.removeById(id);
        recordActivity(principal.getId(), principal.getUsername(), "APIKEY_DELETE", "API_KEY",
                id, key.getName(), "删除 API Key", 1);
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

    private void recordActivity(Long userId, String username, String action, String resourceType,
                                Long resourceId, String resourceName, String detail, int status) {
        UserActivityLog log = new UserActivityLog();
        log.setUserId(userId);
        log.setUsername(username);
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
