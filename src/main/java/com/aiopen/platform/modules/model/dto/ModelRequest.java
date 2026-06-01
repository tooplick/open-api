package com.aiopen.platform.modules.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ModelRequest {

    @NotBlank(message = "模型标识不能为空")
    private String modelName;

    private String displayName;

    private String type = "chat";

    private BigDecimal promptPrice = BigDecimal.ZERO;

    private BigDecimal completionPrice = BigDecimal.ZERO;

    private Integer status = 1;

    private String remark;
}
