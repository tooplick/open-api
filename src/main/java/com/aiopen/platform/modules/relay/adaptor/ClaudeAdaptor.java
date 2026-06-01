package com.aiopen.platform.modules.relay.adaptor;

import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.relay.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.UUID;

/**
 * Anthropic Claude 渠道:OpenAI /chat/completions 与 Claude /v1/messages 互转。
 * 请求: 抽 system 顶层、role 映射、max_tokens 必填; 响应: content blocks -> message.content;
 * 流式: Claude 事件流 -> OpenAI chat.completion.chunk。
 */
@Component
@RequiredArgsConstructor
public class ClaudeAdaptor implements UpstreamAdaptor {

    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final ObjectMapper objectMapper;

    @Override
    public String buildRequestUrl(Channel channel, String requestUri) {
        String base = channel.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/v1/messages";
    }

    @Override
    public void applyAuthHeaders(HttpRequest.Builder builder, String upstreamKey) {
        builder.header("x-api-key", upstreamKey);
        builder.header("anthropic-version", "2023-06-01");
    }

    @Override
    public byte[] convertRequest(JsonNode openAiRequest, String upstreamModel, boolean stream) {
        try {
            ObjectNode claude = objectMapper.createObjectNode();
            claude.put("model", upstreamModel);

            int maxTokens = openAiRequest.path("max_tokens").asInt(0);
            if (maxTokens <= 0) {
                maxTokens = openAiRequest.path("max_completion_tokens").asInt(0);
            }
            claude.put("max_tokens", maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS);

            if (stream) {
                claude.put("stream", true);
            }
            if (openAiRequest.has("temperature")) {
                claude.put("temperature", openAiRequest.path("temperature").asDouble());
            }
            if (openAiRequest.has("top_p")) {
                claude.put("top_p", openAiRequest.path("top_p").asDouble());
            }

            ArrayNode messages = claude.putArray("messages");
            StringBuilder system = new StringBuilder();
            for (JsonNode m : openAiRequest.path("messages")) {
                String role = m.path("role").asText("");
                String content = extractContent(m.path("content"));
                if ("system".equals(role)) {
                    if (system.length() > 0) {
                        system.append("\n");
                    }
                    system.append(content);
                } else {
                    ObjectNode msg = messages.addObject();
                    msg.put("role", "assistant".equals(role) ? "assistant" : "user");
                    msg.put("content", content);
                }
            }
            if (system.length() > 0) {
                claude.put("system", system.toString());
            }
            return objectMapper.writeValueAsBytes(claude);
        } catch (Exception e) {
            throw new IllegalStateException("转换 Claude 请求失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] convertResponse(byte[] upstreamBody, Usage usageOut) {
        try {
            JsonNode claude = objectMapper.readTree(upstreamBody);
            StringBuilder text = new StringBuilder();
            for (JsonNode block : claude.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            JsonNode u = claude.path("usage");
            int input = u.path("input_tokens").asInt(0);
            int output = u.path("output_tokens").asInt(0);
            usageOut.promptTokens = input;
            usageOut.completionTokens = output;
            usageOut.totalTokens = input + output;

            ObjectNode resp = objectMapper.createObjectNode();
            resp.put("id", claude.path("id").asText("chatcmpl-" + UUID.randomUUID()));
            resp.put("object", "chat.completion");
            resp.put("created", System.currentTimeMillis() / 1000);
            resp.put("model", claude.path("model").asText(""));
            ArrayNode choices = resp.putArray("choices");
            ObjectNode choice = choices.addObject();
            choice.put("index", 0);
            ObjectNode message = choice.putObject("message");
            message.put("role", "assistant");
            message.put("content", text.toString());
            choice.put("finish_reason", mapStopReason(claude.path("stop_reason").asText(null)));
            ObjectNode usageNode = resp.putObject("usage");
            usageNode.put("prompt_tokens", input);
            usageNode.put("completion_tokens", output);
            usageNode.put("total_tokens", input + output);
            return objectMapper.writeValueAsBytes(resp);
        } catch (Exception e) {
            // 解析失败(如上游错误体)原样返回
            return upstreamBody;
        }
    }

    @Override
    public String convertStreamLine(String upstreamLine, Usage usageOut, StreamState state) {
        if (upstreamLine == null) {
            return null;
        }
        String trimmed = upstreamLine.trim();
        if (!trimmed.startsWith("data:")) {
            return null; // 忽略 event: 行与空行
        }
        String data = trimmed.substring(5).trim();
        if (data.isEmpty()) {
            return null;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (Exception e) {
            return null;
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "message_start": {
                JsonNode msg = node.path("message");
                state.setId(msg.path("id").asText(state.getId()));
                state.setModel(msg.path("model").asText(state.getModel()));
                JsonNode u = msg.path("usage");
                usageOut.promptTokens = u.path("input_tokens").asInt(usageOut.promptTokens);
                usageOut.completionTokens = u.path("output_tokens").asInt(usageOut.completionTokens);
                state.setRoleSent(true);
                return sseChunk(state, roleDelta(), null);
            }
            case "content_block_delta": {
                String text = node.path("delta").path("text").asText("");
                if (text.isEmpty()) {
                    return null;
                }
                return sseChunk(state, contentDelta(text), null);
            }
            case "message_delta": {
                JsonNode u = node.path("usage");
                usageOut.completionTokens = u.path("output_tokens").asInt(usageOut.completionTokens);
                String stop = node.path("delta").path("stop_reason").asText(null);
                if (stop != null) {
                    state.setFinishReason(mapStopReason(stop));
                }
                return null;
            }
            case "message_stop": {
                usageOut.totalTokens = usageOut.promptTokens + usageOut.completionTokens;
                String finish = state.getFinishReason() == null ? "stop" : state.getFinishReason();
                return sseChunk(state, objectMapper.createObjectNode(), finish) + "data: [DONE]\n\n";
            }
            default:
                return null; // ping / content_block_start / content_block_stop
        }
    }

    private ObjectNode roleDelta() {
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("role", "assistant");
        return delta;
    }

    private ObjectNode contentDelta(String text) {
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("content", text);
        return delta;
    }

    private String sseChunk(StreamState state, ObjectNode delta, String finishReason) {
        ObjectNode chunk = objectMapper.createObjectNode();
        chunk.put("id", state.getId());
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", System.currentTimeMillis() / 1000);
        chunk.put("model", state.getModel());
        ArrayNode choices = chunk.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        choice.set("delta", delta);
        if (finishReason == null) {
            choice.putNull("finish_reason");
        } else {
            choice.put("finish_reason", finishReason);
        }
        return "data: " + chunk + "\n\n";
    }

    private String extractContent(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if ("text".equals(part.path("type").asText())) {
                    sb.append(part.path("text").asText());
                }
            }
            return sb.toString();
        }
        return content.asText("");
    }

    private String mapStopReason(String claudeStop) {
        if (claudeStop == null) {
            return "stop";
        }
        return switch (claudeStop) {
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            default -> "stop"; // end_turn / stop_sequence / 其他
        };
    }
}
