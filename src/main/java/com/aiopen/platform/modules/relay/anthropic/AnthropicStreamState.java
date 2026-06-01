package com.aiopen.platform.modules.relay.anthropic;

import lombok.Data;

import java.util.UUID;

/** Anthropic 入站流式转换的跨行状态(OpenAI SSE -> Anthropic 事件流)。 */
@Data
public class AnthropicStreamState {

    private String id = "msg_" + UUID.randomUUID().toString().replace("-", "");
    private String model = "";
    /** content_block 是否已开启(content_block_start 已发送) */
    private boolean contentBlockOpen;
    /** Anthropic stop_reason(由 OpenAI finish_reason 映射) */
    private String stopReason;
    private int promptTokens;
    private int completionTokens;
}
