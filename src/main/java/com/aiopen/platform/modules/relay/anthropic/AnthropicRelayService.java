package com.aiopen.platform.modules.relay.anthropic;

import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.channel.service.ChannelService;
import com.aiopen.platform.modules.log.entity.Log;
import com.aiopen.platform.modules.log.service.LogService;
import com.aiopen.platform.modules.relay.RelayAuthService;
import com.aiopen.platform.modules.relay.RelayContext;
import com.aiopen.platform.modules.relay.RelayMetrics;
import com.aiopen.platform.modules.relay.Usage;
import com.aiopen.platform.modules.relay.exception.RelayException;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Anthropic 入站转发(POST /anthropic/v1/messages)。
 * 客户端用 Claude/Anthropic SDK(x-api-key 鉴权、Anthropic 报文),网关反向转成 OpenAI 发给上游渠道,
 * 再把响应转回 Anthropic。上游渠道按 OpenAI 兼容对待(/v1/chat/completions)。不计费,仅记 token 日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnthropicRelayService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(300);

    private final RelayAuthService relayAuthService;
    private final ChannelService channelService;
    private final LogService logService;
    private final AnthropicConverter converter;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public void relayMessages(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long start = System.currentTimeMillis();

        RelayContext ctx = relayAuthService.authenticateKey(extractKey(request));

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        JsonNode root;
        String model;
        boolean stream;
        try {
            root = objectMapper.readTree(body);
            model = root.path("model").asText(null);
            stream = root.path("stream").asBoolean(false);
        } catch (Exception e) {
            throw new RelayException(400, "invalid_request_error", "请求体不是合法 JSON");
        }
        if (!StringUtils.hasText(model)) {
            throw new RelayException(400, "invalid_request_error", "缺少 model 参数");
        }
        if (!modelAllowed(ctx.getModelLimits(), model)) {
            throw new RelayException(403, "model_not_allowed", "该 API Key 无权调用模型: " + model);
        }

        Channel channel = channelService.selectChannelForModel(ctx.getGroup(), model);
        if (channel == null) {
            throw new RelayException(404, "model_not_found", "没有可用渠道支持模型: " + model);
        }

        String upstreamModel = applyModelMapping(channel, model);
        byte[] upstreamBody = converter.toOpenAiRequest(root, upstreamModel, stream);
        String url = chatCompletionsUrl(channel);
        String upstreamKey = resolveUpstreamKey(channel);

        Usage usage = new Usage();
        RelayMetrics metrics = new RelayMetrics();
        long upstreamStart = System.currentTimeMillis();
        try {
            if (stream) {
                forwardStreaming(url, upstreamKey, upstreamBody, model, response, usage, metrics, upstreamStart);
            } else {
                forwardBlocking(url, upstreamKey, upstreamBody, model, response, usage, metrics, upstreamStart);
            }
        } catch (Exception e) {
            metrics.upstreamMs = System.currentTimeMillis() - upstreamStart;
            log.error("Anthropic 入站转发失败 channel={} url={}: {}", channel.getName(), url, e.getMessage());
            recordLog(ctx, channel, model, upstreamModel, stream, 2, usage,
                    System.currentTimeMillis() - start, request, metrics, "上游请求异常: " + e.getMessage());
            if (!response.isCommitted()) {
                throw new RelayException(502, "api_error", "上游渠道请求失败");
            }
            return;
        }
        metrics.upstreamMs = System.currentTimeMillis() - upstreamStart;

        boolean success = metrics.status >= 200 && metrics.status < 300;
        recordLog(ctx, channel, model, upstreamModel, stream, success ? 1 : 2, usage,
                System.currentTimeMillis() - start, request, metrics,
                success ? null : "上游返回状态 " + metrics.status);
    }

    private void forwardBlocking(String url, String upstreamKey, byte[] upstreamBody, String requestedModel,
                                 HttpServletResponse response, Usage usage, RelayMetrics metrics, long upstreamStart)
            throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + upstreamKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(upstreamBody))
                .build();
        HttpResponse<byte[]> resp = send(req, HttpResponse.BodyHandlers.ofByteArray());
        metrics.ttfbMs = System.currentTimeMillis() - upstreamStart;
        int code = resp.statusCode();
        metrics.status = code;
        response.setStatus(code);
        response.setContentType("application/json;charset=utf-8");
        byte[] out = (code >= 200 && code < 300)
                ? converter.toAnthropicResponse(resp.body(), requestedModel, usage)
                : converter.errorJson(code, upstreamErrorMessage(resp.body(), code));
        OutputStream os = response.getOutputStream();
        os.write(out);
        os.flush();
    }

    private void forwardStreaming(String url, String upstreamKey, byte[] upstreamBody, String requestedModel,
                                  HttpServletResponse response, Usage usage, RelayMetrics metrics, long upstreamStart)
            throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + upstreamKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(upstreamBody))
                .build();
        HttpResponse<InputStream> resp = send(req, HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        metrics.status = code;
        response.setStatus(code);
        if (code >= 200 && code < 300) {
            response.setContentType("text/event-stream;charset=utf-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            AnthropicStreamState state = new AnthropicStreamState();
            state.setModel(requestedModel);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                PrintWriter writer = response.getWriter();
                writer.write(converter.streamStart(state));
                writer.flush();
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (first) {
                        metrics.ttfbMs = System.currentTimeMillis() - upstreamStart;
                        first = false;
                    }
                    String out = converter.streamDelta(line, state, usage);
                    if (out != null) {
                        writer.write(out);
                        writer.flush();
                    }
                }
                writer.write(converter.streamEnd(state, usage));
                writer.flush();
            }
        } else {
            metrics.ttfbMs = System.currentTimeMillis() - upstreamStart;
            byte[] err = StreamUtils.copyToByteArray(resp.body());
            response.setContentType("application/json;charset=utf-8");
            OutputStream os = response.getOutputStream();
            os.write(converter.errorJson(code, upstreamErrorMessage(err, code)));
            os.flush();
        }
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

    private String chatCompletionsUrl(Channel channel) {
        String base = channel.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/v1/chat/completions";
    }

    private boolean modelAllowed(String modelLimits, String model) {
        if (!StringUtils.hasText(modelLimits)) {
            return true;
        }
        Set<String> allowed = Arrays.stream(modelLimits.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return allowed.isEmpty() || allowed.contains(model);
    }

    private String applyModelMapping(Channel channel, String model) {
        String mapping = channel.getModelMapping();
        if (!StringUtils.hasText(mapping)) {
            return model;
        }
        try {
            JsonNode node = objectMapper.readTree(mapping);
            JsonNode target = node.get(model);
            if (target != null && target.isTextual() && StringUtils.hasText(target.asText())) {
                return target.asText();
            }
        } catch (Exception e) {
            log.warn("解析 channel[{}] modelMapping 失败: {}", channel.getId(), e.getMessage());
        }
        return model;
    }

    private String resolveUpstreamKey(Channel channel) {
        String raw = channel.getApiKey();
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        List<String> keys = Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (keys.isEmpty()) {
            return raw.trim();
        }
        return keys.size() == 1 ? keys.get(0) : keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
    }

    private String upstreamErrorMessage(byte[] body, int code) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode msg = root.path("error").path("message");
            if (msg.isTextual() && StringUtils.hasText(msg.asText())) {
                return msg.asText();
            }
        } catch (Exception ignore) {
            // 非 JSON 错误体
        }
        return "上游返回状态 " + code;
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return httpClient.send(req, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("上游请求被中断", e);
        }
    }

    private void recordLog(RelayContext ctx, Channel channel, String model, String upstreamModel, boolean stream,
                           int type, Usage usage, long durationMs, HttpServletRequest request,
                           RelayMetrics metrics, String content) {
        try {
            Log logEntry = new Log();
            logEntry.setUserId(ctx.getUser().getId());
            logEntry.setUsername(ctx.getUser().getUsername());
            logEntry.setApiKeyId(ctx.getApiKey().getId());
            logEntry.setChannelId(channel.getId());
            logEntry.setChannelName(channel.getName());
            logEntry.setModelName(model);
            logEntry.setUpstreamModel(upstreamModel);
            logEntry.setStream(stream ? 1 : 0);
            logEntry.setEndpoint(request.getRequestURI());
            logEntry.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
            logEntry.setType(type);
            logEntry.setPromptTokens(usage.promptTokens);
            logEntry.setCompletionTokens(usage.completionTokens);
            logEntry.setTotalTokens(usage.totalTokens);
            logEntry.setDurationMs(durationMs);
            if (metrics != null) {
                logEntry.setHttpStatus(metrics.status);
                logEntry.setTtfbMs(metrics.ttfbMs);
                logEntry.setUpstreamMs(metrics.upstreamMs);
            }
            logEntry.setRequestId(UUID.randomUUID().toString());
            logEntry.setIp(getClientIp(request));
            logEntry.setContent(truncate(content, 1000));
            logService.record(logEntry);
        } catch (Exception e) {
            log.warn("写调用日志失败: {}", e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }
}
