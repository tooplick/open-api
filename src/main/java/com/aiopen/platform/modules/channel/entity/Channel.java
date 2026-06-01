package com.aiopen.platform.modules.channel.entity;

import com.aiopen.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("channel")
public class Channel extends BaseEntity {

    @TableId
    private Long id;

    private String name;

    /** openai / azure / anthropic ... */
    private String type;

    /** 上游地址,如 https://api.openai.com */
    private String baseUrl;

    /** 上游密钥,不对外序列化 */
    @JsonIgnore
    private String apiKey;

    /** 支持的模型,逗号分隔 */
    private String models;

    /** 模型重命名映射(JSON),可选 */
    private String modelMapping;

    /** 1启用 0禁用 */
    private Integer status;

    /** 权重(同优先级内按权重随机) */
    private Integer weight;

    /** 优先级,越大越优先 */
    private Integer priority;
}
