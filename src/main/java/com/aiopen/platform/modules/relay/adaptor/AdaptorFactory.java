package com.aiopen.platform.modules.relay.adaptor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按渠道类型返回对应适配器。anthropic/claude -> Claude,其余(openai/azure/...) -> OpenAI 透传。
 */
@Component
@RequiredArgsConstructor
public class AdaptorFactory {

    private final OpenAiAdaptor openAiAdaptor;
    private final ClaudeAdaptor claudeAdaptor;

    public UpstreamAdaptor get(String channelType) {
        if (channelType == null) {
            return openAiAdaptor;
        }
        return switch (channelType.trim().toLowerCase()) {
            case "anthropic", "claude" -> claudeAdaptor;
            default -> openAiAdaptor;
        };
    }
}
