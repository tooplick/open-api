package com.aiopen.platform.modules.relay.anthropic;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Anthropic 入站流式转换的跨行状态(OpenAI SSE -> Anthropic 事件流)。 */
@Data
public class AnthropicStreamState {

    /** content block 类型常量 */
    public static final int BLOCK_NONE = 0;
    public static final int BLOCK_TEXT = 1;
    public static final int BLOCK_TOOL = 2;

    private String id = "msg_" + UUID.randomUUID().toString().replace("-", "");
    private String model = "";
    /** Anthropic stop_reason(由 OpenAI finish_reason 映射) */
    private String stopReason;
    private int promptTokens;
    private int completionTokens;

    /** 下一个要分配的 content block 索引(text 与 tool_use 共享同一索引空间) */
    private int nextIndex = 0;
    /** 当前已开启的 content block 索引;-1 表示当前没有开启的块 */
    private int currentIndex = -1;
    /** 当前已开启块的类型:BLOCK_NONE / BLOCK_TEXT / BLOCK_TOOL */
    private int currentType = BLOCK_NONE;
    /** OpenAI 流式 tool_call 的 index -> 已分配的 Anthropic content block 索引 */
    private final Map<Integer, Integer> toolCallBlocks = new HashMap<>();
}
