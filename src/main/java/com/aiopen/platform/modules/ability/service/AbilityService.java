package com.aiopen.platform.modules.ability.service;

import com.aiopen.platform.modules.ability.entity.Ability;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AbilityService extends IService<Ability> {

    /** 按渠道重建能力行:先删该渠道所有 ability,再按 group x models 笛卡尔展开插入 */
    void rebuildForChannel(Channel channel);

    /** 删除指定渠道的所有能力行 */
    void deleteForChannel(Long channelId);

    /**
     * 选出某 (group, model) 下的可用渠道ID:取最高优先级层,层内按权重随机。
     * 无可用渠道返回 null。
     */
    Long selectChannelId(String group, String model);

    /** 聚合所有可用模型(去重、排序) */
    List<String> distinctModels();

    /** 聚合某分组下的可用模型(去重、排序) */
    List<String> distinctModels(String group);
}
