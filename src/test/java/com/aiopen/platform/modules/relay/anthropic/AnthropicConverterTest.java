package com.aiopen.platform.modules.relay.anthropic;

import com.aiopen.platform.modules.relay.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnthropicConverter converter = new AnthropicConverter(objectMapper);

    @Test
    void convertsTextFunctionSyntaxToAnthropicToolUse() throws Exception {
        ObjectNode openAi = objectMapper.createObjectNode();
        ObjectNode choice = openAi.putArray("choices").addObject();
        ObjectNode message = choice.putObject("message");
        message.put("content", """
                Let me find the current swipe implementation.
                    <function=Grep>
                    <parameter=pattern>swipe|touchmove|touchstart|touchend
                    <parameter=path>C:\\repo\\frontend\\src
                    <parameter=output_mode>files_with_matches

                    <function=Grep>
                    <parameter=pattern>delete|dismiss|dialog
                    <parameter=glob>.vue
                    <parameter=output_mode>files_with_matches
                """);
        choice.put("finish_reason", "stop");
        ObjectNode usageNode = openAi.putObject("usage");
        usageNode.put("prompt_tokens", 11);
        usageNode.put("completion_tokens", 17);
        usageNode.put("total_tokens", 28);

        Usage usage = new Usage();
        byte[] converted = converter.toAnthropicResponse(
                objectMapper.writeValueAsBytes(openAi), "claude-code", usage);

        JsonNode anth = objectMapper.readTree(converted);
        JsonNode content = anth.path("content");
        assertThat(anth.path("stop_reason").asText()).isEqualTo("tool_use");
        assertThat(content).hasSize(3);
        assertThat(content.path(0).path("type").asText()).isEqualTo("text");
        assertThat(content.path(0).path("text").asText()).startsWith("Let me find");
        assertThat(content.path(1).path("type").asText()).isEqualTo("tool_use");
        assertThat(content.path(1).path("name").asText()).isEqualTo("Grep");
        assertThat(content.path(1).path("input").path("pattern").asText())
                .isEqualTo("swipe|touchmove|touchstart|touchend");
        assertThat(content.path(1).path("input").path("path").asText()).isEqualTo("C:\\repo\\frontend\\src");
        assertThat(content.path(2).path("input").path("glob").asText()).isEqualTo(".vue");
        assertThat(usage.totalTokens).isEqualTo(28);
    }

    @Test
    void flushesTextFunctionSyntaxAtEndOfStream() throws Exception {
        AnthropicStreamState state = new AnthropicStreamState();
        Usage usage = new Usage();

        ObjectNode chunk = objectMapper.createObjectNode();
        ObjectNode choice = chunk.putArray("choices").addObject();
        ObjectNode delta = choice.putObject("delta");
        delta.put("content", """
                Searching now.
                <function=Grep>
                <parameter=pattern>swipe
                <parameter=output_mode>files_with_matches
                """);

        String deltaOut = converter.streamDelta(
                "data: " + objectMapper.writeValueAsString(chunk), state, usage);
        assertThat(deltaOut).isNull();

        String end = converter.streamEnd(state, usage);

        assertThat(end).contains("\"type\":\"text\"");
        assertThat(end).contains("\"type\":\"tool_use\"");
        assertThat(end).contains("\"name\":\"Grep\"");
        assertThat(end).contains("input_json_delta");
        assertThat(end).contains("\\\"pattern\\\":\\\"swipe\\\"");
        assertThat(end).contains("\"stop_reason\":\"tool_use\"");
    }

    @Test
    void streamsOrdinaryTextWithoutWaitingForEnd() throws Exception {
        AnthropicStreamState state = new AnthropicStreamState();
        Usage usage = new Usage();

        ObjectNode chunk = objectMapper.createObjectNode();
        ObjectNode choice = chunk.putArray("choices").addObject();
        ObjectNode delta = choice.putObject("delta");
        delta.put("content", "ordinary streamed text");

        String out = converter.streamDelta(
                "data: " + objectMapper.writeValueAsString(chunk), state, usage);

        assertThat(out).contains("content_block_delta");
        assertThat(out).contains("ordinary streamed text");
    }
}
