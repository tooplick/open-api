package com.aiopen.platform.modules.relay;

/** 累计 token 用量,贯穿转发与日志记录(本项目不计费,仅记录)。 */
public class Usage {
    public int promptTokens;
    public int completionTokens;
    public int totalTokens;
}
