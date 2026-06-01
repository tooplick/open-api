package com.aiopen.platform.modules.relay.adaptor;

import lombok.Data;

import java.util.UUID;

/** 流式转换的跨行状态(供 Claude 等有状态协议使用)。 */
@Data
public class StreamState {
    private String id = "chatcmpl-" + UUID.randomUUID();
    private String model = "";
    private String finishReason;
    private boolean roleSent;
}
