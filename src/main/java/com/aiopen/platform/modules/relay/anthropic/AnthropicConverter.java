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
                appendMessage(messages, m);
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
            appendTools(oai, anthropic.get("tools"));
            appendToolChoice(oai, anthropic.get("tool_choice"));
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
            JsonNode message = choice.path("message");
            String content = message.path("content").asText("");
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
            if (!content.isEmpty()) {
                ObjectNode textBlock = contentArr.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", content);
            }
            JsonNode toolCalls = message.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode call : toolCalls) {
                    ObjectNode block = contentArr.addObject();
                    block.put("type", "tool_use");
                    block.put("id", call.path("id").asText());
                    block.put("name", call.path("function").path("name").asText());
                    block.set("input", parseToolArguments(call.path("function").path("arguments").asText("")));
                }
            }
            if (contentArr.isEmpty()) {
                ObjectNode textBlock = contentArr.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", "");
            }
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

        ObjectNode ping = objectMapper.createObjectNode();
        ping.put("type", "ping");

        // content block 不再预开:按需在 streamDelta 中根据 text / tool_use 惰性开启
        return sse("message_start", start) + sse("ping", ping);
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
        if (finish != null && !finish.isEmpty()) {
            state.setStopReason(mapFinishToStop(finish));
        }
        JsonNode delta = choice.path("delta");
        StringBuilder sb = new StringBuilder();

        // 文本增量
        String text = delta.path("content").asText("");
        if (!text.isEmpty()) {
            sb.append(ensureTextBlock(state));
            sb.append(textDelta(state.getCurrentIndex(), text));
        }

        // 工具调用增量(OpenAI delta.tool_calls -> Anthropic tool_use 块)
        JsonNode toolCalls = delta.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (JsonNode tc : toolCalls) {
                int oaiIndex = tc.path("index").asInt(0);
                Integer blockIndex = state.getToolCallBlocks().get(oaiIndex);
                if (blockIndex == null) {
                    // 首次见到该 tool_call:关闭当前块,开一个新的 tool_use 块
                    sb.append(closeCurrentBlock(state));
                    blockIndex = state.getNextIndex();
                    state.setNextIndex(blockIndex + 1);
                    state.getToolCallBlocks().put(oaiIndex, blockIndex);
                    String id = tc.path("id").asText("");
                    if (id.isEmpty()) {
                        id = "toolu_" + java.util.UUID.randomUUID().toString().replace("-", "");
                    }
                    String name = tc.path("function").path("name").asText("");
                    sb.append(toolBlockStart(blockIndex, id, name));
                    state.setCurrentIndex(blockIndex);
                    state.setCurrentType(AnthropicStreamState.BLOCK_TOOL);
                }
                String args = tc.path("function").path("arguments").asText("");
                if (!args.isEmpty()) {
                    sb.append(inputJsonDelta(blockIndex, args));
                }
            }
        }

        return sb.length() == 0 ? null : sb.toString();
    }

    /** 流收尾:content_block_stop + message_delta(stop_reason/usage) + message_stop。 */
    public String streamEnd(AnthropicStreamState state, Usage usageOut) {
        usageOut.totalTokens = usageOut.promptTokens + usageOut.completionTokens;
        StringBuilder sb = new StringBuilder();
        sb.append(closeCurrentBlock(state));
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

    // ---------- 工具转换 helper(请求 / 非流式) ----------

    /** 把一条 Anthropic 消息追加为一条或多条 OpenAI 消息(展开 tool_use / tool_result)。 */
    private void appendMessage(ArrayNode messages, JsonNode m) throws Exception {
        String role = m.path("role").asText("user");
        JsonNode content = m.path("content");
        if ("assistant".equals(role)) {
            StringBuilder text = new StringBuilder();
            ArrayNode toolCalls = null;
            if (content.isArray()) {
                for (JsonNode part : content) {
                    String type = part.path("type").asText();
                    if ("text".equals(type)) {
                        text.append(part.path("text").asText());
                    } else if ("tool_use".equals(type)) {
                        if (toolCalls == null) {
                            toolCalls = objectMapper.createArrayNode();
                        }
                        ObjectNode call = toolCalls.addObject();
                        call.put("id", part.path("id").asText());
                        call.put("type", "function");
                        ObjectNode fn = call.putObject("function");
                        fn.put("name", part.path("name").asText());
                        JsonNode input = part.path("input");
                        fn.put("arguments", input.isMissingNode() || input.isNull()
                                ? "{}" : objectMapper.writeValueAsString(input));
                    }
                }
            } else if (content.isTextual()) {
                text.append(content.asText());
            }
            ObjectNode msg = messages.addObject();
            msg.put("role", "assistant");
            if (text.length() > 0) {
                msg.put("content", text.toString());
            } else if (toolCalls != null) {
                msg.putNull("content");
            } else {
                msg.put("content", "");
            }
            if (toolCalls != null) {
                msg.set("tool_calls", toolCalls);
            }
            return;
        }
        // user / 其它角色:tool_result 拆为独立的 role:"tool" 消息(须紧跟 assistant 的 tool_calls),
        // 其余 text 合并为一条 user 消息。
        if (content.isArray()) {
            StringBuilder userText = new StringBuilder();
            for (JsonNode part : content) {
                String type = part.path("type").asText();
                if ("tool_result".equals(type)) {
                    ObjectNode toolMsg = messages.addObject();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", part.path("tool_use_id").asText());
                    toolMsg.put("content", extractText(part.path("content")));
                } else if ("text".equals(type)) {
                    userText.append(part.path("text").asText());
                }
            }
            if (userText.length() > 0) {
                ObjectNode msg = messages.addObject();
                msg.put("role", "user");
                msg.put("content", userText.toString());
            }
        } else {
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", extractText(content));
        }
    }

    /** Anthropic tools -> OpenAI tools(function calling)。 */
    private void appendTools(ObjectNode oai, JsonNode tools) {
        if (tools == null || !tools.isArray() || tools.isEmpty()) {
            return;
        }
        ArrayNode arr = oai.putArray("tools");
        for (JsonNode t : tools) {
            String name = t.path("name").asText("");
            if (name.isEmpty()) {
                continue;
            }
            ObjectNode tool = arr.addObject();
            tool.put("type", "function");
            ObjectNode fn = tool.putObject("function");
            fn.put("name", name);
            if (t.hasNonNull("description")) {
                fn.put("description", t.path("description").asText());
            }
            JsonNode schema = t.get("input_schema");
            if (schema != null && schema.isObject()) {
                fn.set("parameters", schema);
            } else {
                ObjectNode params = fn.putObject("parameters");
                params.put("type", "object");
                params.putObject("properties");
            }
        }
    }

    /** Anthropic tool_choice -> OpenAI tool_choice。 */
    private void appendToolChoice(ObjectNode oai, JsonNode tc) {
        if (tc == null || !tc.isObject()) {
            return;
        }
        String type = tc.path("type").asText("auto");
        switch (type) {
            case "any" -> oai.put("tool_choice", "required");
            case "none" -> oai.put("tool_choice", "none");
            case "tool" -> {
                ObjectNode choice = oai.putObject("tool_choice");
                choice.put("type", "function");
                choice.putObject("function").put("name", tc.path("name").asText());
            }
            default -> oai.put("tool_choice", "auto");
        }
        if (tc.path("disable_parallel_tool_use").asBoolean(false)) {
            oai.put("parallel_tool_calls", false);
        }
    }

    /** OpenAI tool_call 的 arguments(JSON 字符串)-> Anthropic tool_use.input(JSON 对象)。 */
    private JsonNode parseToolArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(arguments);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    // ---------- 工具转换 helper(流式) ----------

    /** 确保当前处于 text 块;若不是则关闭旧块并开一个新的 text 块,返回需写出的事件。 */
    private String ensureTextBlock(AnthropicStreamState state) {
        if (state.getCurrentType() == AnthropicStreamState.BLOCK_TEXT) {
            return "";
        }
        String pre = closeCurrentBlock(state);
        int index = state.getNextIndex();
        state.setNextIndex(index + 1);
        state.setCurrentIndex(index);
        state.setCurrentType(AnthropicStreamState.BLOCK_TEXT);
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "content_block_start");
        block.put("index", index);
        ObjectNode cb = block.putObject("content_block");
        cb.put("type", "text");
        cb.put("text", "");
        return pre + sse("content_block_start", block);
    }

    /** 关闭当前开启的 content block(若有),返回 content_block_stop 事件。 */
    private String closeCurrentBlock(AnthropicStreamState state) {
        if (state.getCurrentType() == AnthropicStreamState.BLOCK_NONE) {
            return "";
        }
        ObjectNode stop = objectMapper.createObjectNode();
        stop.put("type", "content_block_stop");
        stop.put("index", state.getCurrentIndex());
        state.setCurrentType(AnthropicStreamState.BLOCK_NONE);
        state.setCurrentIndex(-1);
        return sse("content_block_stop", stop);
    }

    private String textDelta(int index, String text) {
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("type", "content_block_delta");
        delta.put("index", index);
        ObjectNode d = delta.putObject("delta");
        d.put("type", "text_delta");
        d.put("text", text);
        return sse("content_block_delta", delta);
    }

    private String toolBlockStart(int index, String id, String name) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "content_block_start");
        block.put("index", index);
        ObjectNode cb = block.putObject("content_block");
        cb.put("type", "tool_use");
        cb.put("id", id);
        cb.put("name", name);
        cb.putObject("input");
        return sse("content_block_start", block);
    }

    private String inputJsonDelta(int index, String partialJson) {
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("type", "content_block_delta");
        delta.put("index", index);
        ObjectNode d = delta.putObject("delta");
        d.put("type", "input_json_delta");
        d.put("partial_json", partialJson);
        return sse("content_block_delta", delta);
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
