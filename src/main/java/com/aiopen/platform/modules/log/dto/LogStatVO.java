package com.aiopen.platform.modules.log.dto;

import lombok.Data;

@Data
public class LogStatVO {

    private long requests;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
}
