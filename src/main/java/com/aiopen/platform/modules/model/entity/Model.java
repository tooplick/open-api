package com.aiopen.platform.modules.model.entity;

import com.aiopen.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model")
public class Model extends BaseEntity {

    @TableId
    private Long id;

    /** 模型标识,如 gpt-4o */
    private String modelName;

    private String displayName;

    /** chat / embedding / image */
    private String type;

    /** 输入每 token 消耗点数 */
    private BigDecimal promptPrice;

    /** 输出每 token 消耗点数 */
    private BigDecimal completionPrice;

    /** 1启用 0禁用 */
    private Integer status;

    private String remark;
}
