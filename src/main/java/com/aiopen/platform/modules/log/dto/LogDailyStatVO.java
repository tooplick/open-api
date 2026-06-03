package com.aiopen.platform.modules.log.dto;

import lombok.Data;

/** 按天聚合的用量统计,用于 Dashboard 趋势图。 */
@Data
public class LogDailyStatVO {

    /** yyyy-MM-dd */
    private String date;
    private long requests;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
}
