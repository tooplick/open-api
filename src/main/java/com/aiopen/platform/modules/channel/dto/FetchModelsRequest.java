package com.aiopen.platform.modules.channel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 从上游拉取可用模型列表的入参。编辑场景下密钥可留空 —— 此时用库中该渠道的原密钥。
 */
@Data
public class FetchModelsRequest {

    @NotBlank(message = "上游地址不能为空")
    private String baseUrl;

    /** 上游密钥;留空且传了 id 时,用库中该渠道的原密钥 */
    private String apiKey;

    /** 渠道 id(编辑场景),用于在密钥留空时取库中原密钥 */
    private Long id;
}
