package com.aiopen.platform.modules.relay.adaptor;

import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.relay.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;

/**
 * OpenAI 兼容渠道:请求/响应直通,仅替换模型名,usage 从标准 usage 字段抓取。
 */
@Component
@RequiredArgsConstructor
public class OpenAiAdaptor implements UpstreamAdaptor {

    private final ObjectMapper objectMapper;

    @Override
    public String buildRequestUrl(Channel channel, String requestUri) {
        String base = channel.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + requestUri;
    }

    @Override
    public void applyAuthHeaders(HttpRequest.Builder builder, String upstreamKey) {
        builder.header("Authorization", "Bearer " + upstreamKey);
    }

    @Override
    public byte[] convertRequest(JsonNode openAiRequest, String upstreamModel, boolean stream) {
        try {
            if (openAiRequest instanceof ObjectNode obj) {
                obj.put("model", upstreamModel);
                return objectMapper.writeValueAsBytes(obj);
            }
            return objectMapper.writeValueAsBytes(openAiRequest);
        } catch (Exception e) {
            throw new IllegalStateException("序列化上游请求失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] convertResponse(byte[] upstreamBody, Usage usageOut) {
        try {
            JsonNode root = objectMapper.readTree(upstreamBody);
            applyUsage(root.get("usage"), usageOut);
        } catch (Exception ignore) {
            // 非 JSON 或无 usage 时忽略
        }
        return upstreamBody;
    }

    @Override
    public String convertStreamLine(String upstreamLine, Usage usageOut, StreamState state) {
        if (upstreamLine != null && upstreamLine.startsWith("data:")) {
            String data = upstreamLine.substring(5).trim();
            if (!data.isEmpty() && !"[DONE]".equals(data)) {
                try {
                    JsonNode root = objectMapper.readTree(data);
                    applyUsage(root.get("usage"), usageOut);
                } catch (Exception ignore) {
                    // 单分片解析失败忽略
                }
            }
        }
        // 原样重建 SSE 分帧
        return upstreamLine + "\n";
    }

    private void applyUsage(JsonNode usageNode, Usage usageOut) {
        if (usageNode == null || usageNode.isNull()) {
            return;
        }
        int prompt = usageNode.path("prompt_tokens").asInt(0);
        int completion = usageNode.path("completion_tokens").asInt(0);
        usageOut.promptTokens = prompt;
        usageOut.completionTokens = completion;
        usageOut.totalTokens = usageNode.path("total_tokens").asInt(prompt + completion);
    }
}
