package com.aiopen.platform.modules.relay;

import com.aiopen.platform.modules.apikey.service.ApiKeyService;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.channel.service.ChannelService;
import com.aiopen.platform.modules.log.entity.Log;
import com.aiopen.platform.modules.log.service.LogService;
import com.aiopen.platform.modules.model.entity.Model;
import com.aiopen.platform.modules.model.service.ModelService;
import com.aiopen.platform.modules.relay.exception.RelayException;
import com.aiopen.platform.modules.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 请求转发核心。鉴权 -> 选路 -> 转发上游(流式/非流式)-> 计费 -> 记日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(300);

    private final RelayAuthService relayAuthService;
    private final ChannelService channelService;
    private final ModelService modelService;
    private final UserService userService;
    private final ApiKeyService apiKeyService;
    private final LogService logService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public void relayChat(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long start = System.currentTimeMillis();

        RelayContext ctx = relayAuthService.authenticate(request.getHeader("Authorization"));

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());

        String model;
        boolean stream;
        try {
            JsonNode root = objectMapper.readTree(body);
            model = root.path("model").asText(null);
            stream = root.path("stream").asBoolean(false);
        } catch (Exception e) {
            throw new RelayException(400, "invalid_request_error", "请求体不是合法 JSON");
        }
        if (!StringUtils.hasText(model)) {
            throw new RelayException(400, "invalid_request_error", "缺少 model 参数");
        }

        Channel channel = channelService.selectChannelForModel(model);
        if (channel == null) {
            throw new RelayException(404, "model_not_found", "没有可用渠道支持模型: " + model);
        }

        String url = buildUrl(channel.getBaseUrl(), request.getRequestURI());
        Usage usage = new Usage();
        int status;
        try {
            status = stream
                    ? forwardStreaming(url, channel.getApiKey(), body, response, usage)
                    : forwardBlocking(url, channel.getApiKey(), body, response, usage);
        } catch (Exception e) {
            log.error("转发上游失败 channel={} url={}: {}", channel.getName(), url, e.getMessage());
            recordLog(ctx, channel, model, 2, usage, 0L, System.currentTimeMillis() - start, request,
                    "上游请求异常: " + e.getMessage());
            if (!response.isCommitted()) {
                throw new RelayException(502, "upstream_error", "上游渠道请求失败");
            }
            return;
        }

        boolean success = status >= 200 && status < 300;
        long cost = success ? computeQuota(model, usage) : 0L;
        if (cost > 0) {
            userService.addUsedQuota(ctx.getUser().getId(), cost);
            apiKeyService.addUsedQuota(ctx.getApiKey().getId(), cost);
        }
        recordLog(ctx, channel, model, success ? 1 : 2, usage, cost,
                System.currentTimeMillis() - start, request, success ? null : "上游返回状态 " + status);
    }

    private int forwardBlocking(String url, String channelKey, byte[] body,
                                HttpServletResponse response, Usage usage) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + channelKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> resp = send(req, HttpResponse.BodyHandlers.ofByteArray());
        byte[] respBody = resp.body();
        response.setStatus(resp.statusCode());
        response.setContentType(resp.headers().firstValue("content-type").orElse("application/json;charset=utf-8"));
        OutputStream os = response.getOutputStream();
        os.write(respBody);
        os.flush();
        parseUsage(respBody, usage);
        return resp.statusCode();
    }

    private int forwardStreaming(String url, String channelKey, byte[] body,
                                 HttpServletResponse response, Usage usage) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + channelKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<InputStream> resp = send(req, HttpResponse.BodyHandlers.ofInputStream());
        response.setStatus(resp.statusCode());
        response.setContentType(resp.headers().firstValue("content-type").orElse("text/event-stream;charset=utf-8"));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            PrintWriter writer = response.getWriter();
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write("\n");
                writer.flush();
                captureStreamUsage(line, usage);
            }
        }
        return resp.statusCode();
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return httpClient.send(req, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("上游请求被中断", e);
        }
    }

    private void parseUsage(byte[] body, Usage usage) {
        try {
            JsonNode root = objectMapper.readTree(body);
            applyUsage(root.get("usage"), usage);
        } catch (Exception ignore) {
            // 非 JSON 或无 usage 字段时忽略
        }
    }

    private void captureStreamUsage(String line, Usage usage) {
        if (line == null || !line.startsWith("data:")) {
            return;
        }
        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            applyUsage(root.get("usage"), usage);
        } catch (Exception ignore) {
            // 单个分片解析失败忽略
        }
    }

    private void applyUsage(JsonNode usageNode, Usage usage) {
        if (usageNode == null || usageNode.isNull()) {
            return;
        }
        int prompt = usageNode.path("prompt_tokens").asInt(0);
        int completion = usageNode.path("completion_tokens").asInt(0);
        usage.promptTokens = prompt;
        usage.completionTokens = completion;
        usage.totalTokens = usageNode.path("total_tokens").asInt(prompt + completion);
    }

    private long computeQuota(String modelName, Usage usage) {
        Model model = modelService.getByName(modelName);
        if (model == null) {
            return 0L;
        }
        BigDecimal promptPrice = model.getPromptPrice() == null ? BigDecimal.ZERO : model.getPromptPrice();
        BigDecimal completionPrice = model.getCompletionPrice() == null ? BigDecimal.ZERO : model.getCompletionPrice();
        BigDecimal cost = promptPrice.multiply(BigDecimal.valueOf(usage.promptTokens))
                .add(completionPrice.multiply(BigDecimal.valueOf(usage.completionTokens)));
        return cost.setScale(0, RoundingMode.CEILING).longValue();
    }

    private void recordLog(RelayContext ctx, Channel channel, String model, int type, Usage usage,
                           long cost, long durationMs, HttpServletRequest request, String content) {
        try {
            Log logEntry = new Log();
            logEntry.setUserId(ctx.getUser().getId());
            logEntry.setUsername(ctx.getUser().getUsername());
            logEntry.setApiKeyId(ctx.getApiKey().getId());
            logEntry.setChannelId(channel.getId());
            logEntry.setChannelName(channel.getName());
            logEntry.setModelName(model);
            logEntry.setType(type);
            logEntry.setPromptTokens(usage.promptTokens);
            logEntry.setCompletionTokens(usage.completionTokens);
            logEntry.setTotalTokens(usage.totalTokens);
            logEntry.setQuota(cost);
            logEntry.setDurationMs(durationMs);
            logEntry.setRequestId(UUID.randomUUID().toString());
            logEntry.setIp(getClientIp(request));
            logEntry.setContent(content);
            logService.record(logEntry);
        } catch (Exception e) {
            log.warn("写调用日志失败: {}", e.getMessage());
        }
    }

    private String buildUrl(String baseUrl, String uri) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + uri;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    /** 累计 token 用量 */
    private static class Usage {
        int promptTokens;
        int completionTokens;
        int totalTokens;
    }
}
