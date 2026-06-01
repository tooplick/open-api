package com.aiopen.platform.modules.relay.anthropic;

import com.aiopen.platform.modules.ability.service.AbilityService;
import com.aiopen.platform.modules.relay.RelayAuthService;
import com.aiopen.platform.modules.relay.RelayContext;
import com.aiopen.platform.modules.relay.exception.RelayException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Anthropic 兼容入站入口(/anthropic/**)。客户端把 base_url 设为 .../anthropic,Claude/Anthropic SDK 会
 * 自动拼出 /anthropic/v1/messages。用 x-api-key 鉴权,错误以 Anthropic 格式返回(本地 @ExceptionHandler,
 * 不走全局 Result 包装)。注意:AuthInterceptor 仅拦截 /api/**,故本前缀天然走 API Key 鉴权。
 */
@Slf4j
@RestController
@RequestMapping("/anthropic")
@RequiredArgsConstructor
public class AnthropicController {

    private final AnthropicRelayService anthropicRelayService;
    private final RelayAuthService relayAuthService;
    private final AbilityService abilityService;
    private final AnthropicConverter converter;

    @PostMapping("/v1/messages")
    public void messages(HttpServletRequest request, HttpServletResponse response) throws IOException {
        anthropicRelayService.relayMessages(request, response);
    }

    @GetMapping("/v1/models")
    public Map<String, Object> models(HttpServletRequest request) {
        RelayContext ctx = relayAuthService.authenticateKey(extractKey(request));
        List<String> models = abilityService.distinctModels(ctx.getGroup());
        if (StringUtils.hasText(ctx.getModelLimits())) {
            Set<String> allowed = Arrays.stream(ctx.getModelLimits().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            if (!allowed.isEmpty()) {
                models = models.stream().filter(allowed::contains).collect(Collectors.toList());
            }
        }
        List<Map<String, Object>> data = models.stream().map(this::toModelObject).toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("data", data);
        resp.put("has_more", false);
        resp.put("first_id", data.isEmpty() ? null : data.get(0).get("id"));
        resp.put("last_id", data.isEmpty() ? null : data.get(data.size() - 1).get("id"));
        return resp;
    }

    private Map<String, Object> toModelObject(String modelName) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "model");
        o.put("id", modelName);
        o.put("display_name", modelName);
        o.put("created_at", "1970-01-01T00:00:00Z");
        return o;
    }

    private String extractKey(HttpServletRequest request) {
        String key = request.getHeader("x-api-key");
        if (StringUtils.hasText(key)) {
            return key.trim();
        }
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
        }
        return null;
    }

    @ExceptionHandler(RelayException.class)
    public ResponseEntity<Map<String, Object>> handleRelay(RelayException e, HttpServletResponse response) {
        if (response.isCommitted()) {
            return null;
        }
        return ResponseEntity.status(e.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(converter.errorMap(e.getHttpStatus(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e, HttpServletResponse response) {
        log.error("anthropic 入站未预期异常", e);
        if (response.isCommitted()) {
            return null;
        }
        return ResponseEntity.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .body(converter.errorMap(500, "服务器内部错误"));
    }
}
