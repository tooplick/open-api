package com.aiopen.platform.modules.ability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 渠道能力路由表:渠道的 group x models 笛卡尔展开,由渠道增改自动维护。
 * 不继承 BaseEntity(派生数据,无 create/update time、无逻辑删除),与 Log 同理。
 */
@Data
@TableName("ability")
public class Ability {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分组(group 是 MySQL 保留字,需反引号) */
    @TableField("`group`")
    private String group;

    /** 模型 */
    @TableField("`model`")
    private String model;

    private Long channelId;

    /** 是否可用: 1可用 0不可用(跟随渠道状态) */
    private Integer enabled;

    /** 优先级(拷贝自渠道) */
    private Integer priority;

    /** 权重(拷贝自渠道) */
    private Integer weight;
}
