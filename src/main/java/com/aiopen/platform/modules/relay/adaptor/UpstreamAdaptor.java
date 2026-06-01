package com.aiopen.platform.modules.relay.adaptor;

import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.relay.Usage;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpRequest;

/**
 * 上游协议适配器:把 OpenAI 兼容的入站请求转换为各 provider 的私有格式,响应再转回 OpenAI 格式。
 */
public interface UpstreamAdaptor {

    /** 上游请求 URL(渠道 baseUrl + 端点) */
    String buildRequestUrl(Channel channel, String requestUri);

    /** 设置上游鉴权头 */
    void applyAuthHeaders(HttpRequest.Builder builder, String upstreamKey);

    /** OpenAI 请求体 -> 上游请求体(含模型名替换) */
    byte[] convertRequest(JsonNode openAiRequest, String upstreamModel, boolean stream);

    /** 非流式: 上游响应体 -> OpenAI 响应体,并抽取 usage */
    byte[] convertResponse(byte[] upstreamBody, Usage usageOut);

    /**
     * 流式: 处理一行上游 SSE,返回要写给客户端的内容(可能含多行/SSE 分帧),或 null 表示跳过此行。
     * 同时抽取 usage。
     */
    String convertStreamLine(String upstreamLine, Usage usageOut, StreamState state);
}
