package com.aiopen.platform.modules.relay.anthropic;

import com.aiopen.platform.modules.relay.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anthropic 入站协议转换器:把客户端的 Anthropic /v1/messages 请求转为 OpenAI /chat/completions,
 * 再把(OpenAI 格式的)上游响应转回 Anthropic 格式。用于"客户端讲 Anthropic、上游讲 OpenAI"的方向,
 * 与 ClaudeAdaptor(上游讲 Anthropic)方向相反。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicConverter {

    private final ObjectMapper objectMapper;

    // ---------- 请求: Anthropic -> OpenAI ----------

    public byte[] toOpenAiRequest(JsonNode anthropic, String upstreamModel, boolean stream) {
        try {
            ObjectNode oai = objectMapper.createObjectNode();
            oai.put("model", upstreamModel);

            ArrayNode messages = oai.putArray("messages");
            String system = extractSystem(anthropic.path("system"));
            if (!system.isEmpty()) {
                ObjectNode sys = messages.addObject();
                sys.put("role", "system");
                sys.put("content", system);
            }
            for (JsonNode m : anthropic.path("messages")) {
                String role = m.path("role").asText("user");
                ObjectNode msg = messages.addObject();
                msg.put("role", "assistant".equals(role) ? "assistant" : "user");
                msg.put("content", extractText(m.path("content")));
            }

            int maxTokens = anthropic.path("max_tokens").asInt(0);
            if (maxTokens > 0) {
                oai.put("max_tokens", maxTokens);
            }
            if (anthropic.has("temperature")) {
                oai.put("temperature", anthropic.path("temperature").asDouble());
            }
            if (anthropic.has("top_p")) {
                oai.put("top_p", anthropic.path("top_p").asDouble());
            }
            JsonNode stops = anthropic.get("stop_sequences");
            if (stops != null && stops.isArray() && !stops.isEmpty()) {
                oai.set("stop", stops);
            }
            if (stream) {
                oai.put("stream", true);
                // 让 OpenAI 兼容上游在流末尾补发 usage,便于换算 output_tokens 与记日志
                ObjectNode so = oai.putObject("stream_options");
                so.put("include_usage", true);
            }
            return objectMapper.writeValueAsBytes(oai);
        } catch (Exception e) {
            throw new IllegalStateException("转换 Anthropic 请求失败: " + e.getMessage(), e);
        }
    }

    // ---------- 非流式响应: OpenAI -> Anthropic ----------

    public byte[] toAnthropicResponse(byte[] openAiBody, String requestedModel, Usage usageOut) {
        try {
            JsonNode oai = objectMapper.readTree(openAiBody);
            JsonNode choice = oai.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            String finish = choice.path("finish_reason").asText(null);

            JsonNode u = oai.path("usage");
            int input = u.path("prompt_tokens").asInt(0);
            int output = u.path("completion_tokens").asInt(0);
            usageOut.promptTokens = input;
            usageOut.completionTokens = output;
            usageOut.totalTokens = u.path("total_tokens").asInt(input + output);

            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("id", "msg_" + java.util.UUID.randomUUID().toString().replace("-", ""));
            msg.put("type", "message");
            msg.put("role", "assistant");
            msg.put("model", requestedModel);
            ArrayNode contentArr = msg.putArray("content");
            ObjectNode textBlock = contentArr.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", content);
            msg.put("stop_reason", mapFinishToStop(finish));
            msg.putNull("stop_sequence");
            ObjectNode usageNode = msg.putObject("usage");
            usageNode.put("input_tokens", input);
            usageNode.put("output_tokens", output);
            return objectMapper.writeValueAsBytes(msg);
        } catch (Exception e) {
            log.warn("转换 OpenAI 响应为 Anthropic 失败,原样返回: {}", e.getMessage());
            return openAiBody;
        }
    }

    // ---------- 流式: OpenAI SSE -> Anthropic 事件流 ----------

    /** 流开头三连:message_start + content_block_start + ping。 */
    public String streamStart(AnthropicStreamState state) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("id", state.getId());
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("model", state.getModel());
        message.putArray("content");
        message.putNull("stop_reason");
        message.putNull("stop_sequence");
        ObjectNode usage = message.putObject("usage");
        usage.put("input_tokens", state.getPromptTokens());
        usage.put("output_tokens", 0);
        ObjectNode start = objectMapper.createObjectNode();
        start.put("type", "message_start");
        start.set("message", message);

        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "content_block_start");
        block.put("index", 0);
        ObjectNode cb = block.putObject("content_block");
        cb.put("type", "text");
        cb.put("text", "");

        ObjectNode ping = objectMapper.createObjectNode();
        ping.put("type", "ping");

        state.setContentBlockOpen(true);
        return sse("message_start", start) + sse("content_block_start", block) + sse("ping", ping);
    }

    /** 处理一行 OpenAI SSE,返回要写出的 Anthropic 事件(通常是 content_block_delta),无内容返回 null。 */
    public String streamDelta(String openAiLine, AnthropicStreamState state, Usage usageOut) {
        if (openAiLine == null) {
            return null;
        }
        String trimmed = openAiLine.trim();
        if (!trimmed.startsWith("data:")) {
            return null;
        }
        String data = trimmed.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (Exception e) {
            return null;
        }
        captureUsage(node.get("usage"), state, usageOut);

        JsonNode choice = node.path("choices").path(0);
        String finish = choice.path("finish_reason").asText(null);
        if (finish != null) {
            state.setStopReason(mapFinishToStop(finish));
        }
        String text = choice.path("delta").path("content").asText("");
        if (text.isEmpty()) {
            return null;
        }
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("type", "content_block_delta");
        delta.put("index", 0);
        ObjectNode d = delta.putObject("delta");
        d.put("type", "text_delta");
        d.put("text", text);
        return sse("content_block_delta", delta);
    }

    /** 流收尾:content_block_stop + message_delta(stop_reason/usage) + message_stop。 */
    public String streamEnd(AnthropicStreamState state, Usage usageOut) {
        usageOut.totalTokens = usageOut.promptTokens + usageOut.completionTokens;
        StringBuilder sb = new StringBuilder();
        if (state.isContentBlockOpen()) {
            ObjectNode stop = objectMapper.createObjectNode();
            stop.put("type", "content_block_stop");
            stop.put("index", 0);
            sb.append(sse("content_block_stop", stop));
        }
        ObjectNode md = objectMapper.createObjectNode();
        md.put("type", "message_delta");
        ObjectNode delta = md.putObject("delta");
        delta.put("stop_reason", state.getStopReason() == null ? "end_turn" : state.getStopReason());
        delta.putNull("stop_sequence");
        ObjectNode usage = md.putObject("usage");
        usage.put("output_tokens", state.getCompletionTokens());
        sb.append(sse("message_delta", md));

        ObjectNode ms = objectMapper.createObjectNode();
        ms.put("type", "message_stop");
        sb.append(sse("message_stop", ms));
        return sb.toString();
    }

    // ---------- 错误 ----------

    public byte[] errorJson(int status, String message) {
        try {
            return objectMapper.writeValueAsBytes(errorMap(status, message));
        } catch (Exception e) {
            return ("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"" + message + "\"}}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public Map<String, Object> errorMap(int status, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", errorType(status));
        error.put("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "error");
        body.put("error", error);
        return body;
    }

    public String errorType(int status) {
        return switch (status) {
            case 400 -> "invalid_request_error";
            case 401 -> "authentication_error";
            case 403 -> "permission_error";
            case 404 -> "not_found_error";
            case 429 -> "rate_limit_error";
            default -> status >= 500 ? "api_error" : "invalid_request_error";
        };
    }

    // ---------- 内部 ----------

    private void captureUsage(JsonNode usageNode, AnthropicStreamState state, Usage usageOut) {
        if (usageNode == null || usageNode.isNull()) {
            return;
        }
        int prompt = usageNode.path("prompt_tokens").asInt(state.getPromptTokens());
        int completion = usageNode.path("completion_tokens").asInt(state.getCompletionTokens());
        state.setPromptTokens(prompt);
        state.setCompletionTokens(completion);
        usageOut.promptTokens = prompt;
        usageOut.completionTokens = completion;
    }

    private String sse(String event, ObjectNode data) {
        return "event: " + event + "\ndata: " + data + "\n\n";
    }

    private String extractSystem(JsonNode system) {
        if (system == null || system.isNull()) {
            return "";
        }
        if (system.isTextual()) {
            return system.asText();
        }
        return extractText(system);
    }

    private String extractText(JsonNode content) {
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

    private String mapFinishToStop(String finish) {
        if (finish == null) {
            return "end_turn";
        }
        return switch (finish) {
            case "length" -> "max_tokens";
            case "tool_calls", "function_call" -> "tool_use";
            case "content_filter" -> "end_turn";
            default -> "end_turn"; // stop / 其他
        };
    }
}
