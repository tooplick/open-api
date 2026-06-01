package com.aiopen.platform.modules.relay;

import com.aiopen.platform.modules.model.entity.Model;
import com.aiopen.platform.modules.model.service.ModelService;
import com.aiopen.platform.modules.relay.exception.RelayException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容转发入口(/v1/**)。使用 API Key 鉴权,错误以 OpenAI 格式返回。
 * 本控制器的异常由本地 @ExceptionHandler 处理,不走全局 Result 包装。
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class RelayController {

    private final RelayService relayService;
    private final RelayAuthService relayAuthService;
    private final ModelService modelService;

    @PostMapping("/chat/completions")
    public void chatCompletions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        relayService.relayChat(request, response);
    }

    @GetMapping("/models")
    public Map<String, Object> models(HttpServletRequest request) {
        relayAuthService.authenticate(request.getHeader("Authorization"));
        List<Map<String, Object>> data = modelService.listEnabled().stream()
                .map(this::toModelObject)
                .toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("object", "list");
        resp.put("data", data);
        return resp;
    }

    private Map<String, Object> toModelObject(Model model) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", model.getModelName());
        o.put("object", "model");
        o.put("created", 0);
        o.put("owned_by", "ai-open-platform");
        return o;
    }

    @ExceptionHandler(RelayException.class)
    public ResponseEntity<Map<String, Object>> handleRelay(RelayException e, HttpServletResponse response) {
        if (response.isCommitted()) {
            return null;
        }
        return ResponseEntity.status(e.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError(e.getMessage(), e.getType()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e, HttpServletResponse response) {
        log.error("relay 未预期异常", e);
        if (response.isCommitted()) {
            return null;
        }
        return ResponseEntity.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("服务器内部错误", "internal_error"));
    }

    private Map<String, Object> openAiError(String message, String type) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        error.put("type", type);
        error.put("code", null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        return body;
    }
}
