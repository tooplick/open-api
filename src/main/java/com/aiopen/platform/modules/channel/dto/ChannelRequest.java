package com.aiopen.platform.modules.channel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChannelRequest {

    @NotBlank(message = "渠道名称不能为空")
    private String name;

    @NotBlank(message = "渠道类型不能为空")
    private String type = "openai";

    @NotBlank(message = "上游地址不能为空")
    private String baseUrl;

    @NotBlank(message = "上游密钥不能为空")
    private String apiKey;

    /** 支持的模型,逗号分隔 */
    @NotBlank(message = "支持的模型不能为空")
    private String models;

    private String modelMapping;

    private Integer weight = 1;

    private Integer priority = 0;

    private Integer status = 1;
}
