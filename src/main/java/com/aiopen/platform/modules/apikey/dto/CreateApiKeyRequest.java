package com.aiopen.platform.modules.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateApiKeyRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    /** 独立额度上限,0 表示跟随用户额度 */
    private Long quota = 0L;

    /** 过期时间,null 表示永不过期 */
    private LocalDateTime expireTime;
}
