package com.aiopen.platform.modules.relay;

import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.channel.service.ChannelService;
import com.aiopen.platform.modules.log.entity.Log;
import com.aiopen.platform.modules.log.service.LogService;
import com.aiopen.platform.modules.relay.adaptor.AdaptorFactory;
import com.aiopen.platform.modules.relay.adaptor.StreamState;
import com.aiopen.platform.modules.relay.adaptor.UpstreamAdaptor;
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
 * 请求转发核心。鉴权 -> 选路 -> 选适配器 -> 转换并转发上游(流式/非流式)-> 记 token 日志。不计费。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(300);

    private final RelayAuthService relayAuthService;
    private final ChannelService channelService;
    private final LogService logService;
    private final AdaptorFactory adaptorFactory;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public void relayChat(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long start = System.currentTimeMillis();

        RelayContext ctx = relayAuthService.authenticate(request.getHeader("Authorization"));

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

        UpstreamAdaptor adaptor = adaptorFactory.get(channel.getType());
        String upstreamModel = applyModelMapping(channel, model);
        String upstreamKey = resolveUpstreamKey(channel);
        String url = adaptor.buildRequestUrl(channel, request.getRequestURI());
        byte[] upstreamBody = adaptor.convertRequest(root, upstreamModel, stream);

        Usage usage = new Usage();
        int status;
        try {
            status = stream
                    ? forwardStreaming(adaptor, url, upstreamKey, upstreamBody, response, usage)
                    : forwardBlocking(adaptor, url, upstreamKey, upstreamBody, response, usage);
        } catch (Exception e) {
            log.error("转发上游失败 channel={} url={}: {}", channel.getName(), url, e.getMessage());
            recordLog(ctx, channel, model, 2, usage, System.currentTimeMillis() - start, request,
                    "上游请求异常: " + e.getMessage());
            if (!response.isCommitted()) {
                throw new RelayException(502, "upstream_error", "上游渠道请求失败");
            }
            return;
        }

        boolean success = status >= 200 && status < 300;
        recordLog(ctx, channel, model, success ? 1 : 2, usage,
                System.currentTimeMillis() - start, request, success ? null : "上游返回状态 " + status);
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

    /** 解析 channel.modelMapping(JSON), 把请求模型名映射为上游模型名;无映射则原样返回。 */
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

    /** 取上游 key:支持换行分隔多 key,随机选一个;单 key 直接返回。 */
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
        if (keys.size() == 1) {
            return keys.get(0);
        }
        return keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
    }

    private int forwardBlocking(UpstreamAdaptor adaptor, String url, String upstreamKey, byte[] upstreamBody,
                                HttpServletResponse response, Usage usage) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(upstreamBody));
        adaptor.applyAuthHeaders(builder, upstreamKey);
        HttpResponse<byte[]> resp = send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int code = resp.statusCode();
        byte[] clientBody = (code >= 200 && code < 300)
                ? adaptor.convertResponse(resp.body(), usage)
                : resp.body();
        response.setStatus(code);
        response.setContentType("application/json;charset=utf-8");
        OutputStream os = response.getOutputStream();
        os.write(clientBody);
        os.flush();
        return code;
    }

    private int forwardStreaming(UpstreamAdaptor adaptor, String url, String upstreamKey, byte[] upstreamBody,
                                 HttpServletResponse response, Usage usage) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(upstreamBody));
        adaptor.applyAuthHeaders(builder, upstreamKey);
        HttpResponse<InputStream> resp = send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        response.setStatus(code);
        if (code >= 200 && code < 300) {
            response.setContentType("text/event-stream;charset=utf-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            StreamState state = new StreamState();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                PrintWriter writer = response.getWriter();
                String line;
                while ((line = reader.readLine()) != null) {
                    String out = adaptor.convertStreamLine(line, usage, state);
                    if (out != null) {
                        writer.write(out);
                        writer.flush();
                    }
                }
            }
        } else {
            byte[] err = StreamUtils.copyToByteArray(resp.body());
            response.setContentType("application/json;charset=utf-8");
            OutputStream os = response.getOutputStream();
            os.write(err);
            os.flush();
        }
        return code;
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return httpClient.send(req, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("上游请求被中断", e);
        }
    }

    private void recordLog(RelayContext ctx, Channel channel, String model, int type, Usage usage,
                           long durationMs, HttpServletRequest request, String content) {
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
            logEntry.setDurationMs(durationMs);
            logEntry.setRequestId(UUID.randomUUID().toString());
            logEntry.setIp(getClientIp(request));
            logEntry.setContent(content);
            logService.record(logEntry);
        } catch (Exception e) {
            log.warn("写调用日志失败: {}", e.getMessage());
        }
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
