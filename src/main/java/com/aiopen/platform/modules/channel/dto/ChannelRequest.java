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

    /** 上游密钥。新建必填;编辑留空表示沿用库中原密钥(由 Service 校验) */
    private String apiKey;

    /** 支持的模型,逗号分隔 */
    @NotBlank(message = "支持的模型不能为空")
    private String models;

    /** 分组,逗号分隔,可属多组 */
    private String group = "default";

    private String modelMapping;

    private Integer weight = 1;

    private Integer priority = 0;

    private Integer status = 1;
}
