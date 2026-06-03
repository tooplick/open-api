package com.aiopen.platform.modules.relay;

/** 一次转发的诊断指标:上游状态码、首字延迟、上游耗时。仅用于写日志。 */
public class RelayMetrics {
    /** 上游 HTTP 状态码;转发抛异常时为 0 */
    public int status;
    /** 首字延迟(毫秒):从发起上游请求到收到首个响应字节/行 */
    public long ttfbMs;
    /** 上游耗时(毫秒):从发起上游请求到读完整个响应 */
    public long upstreamMs;
}
