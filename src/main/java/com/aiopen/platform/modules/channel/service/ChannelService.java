package com.aiopen.platform.modules.channel.service;

import com.aiopen.platform.modules.channel.dto.ChannelRequest;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ChannelService extends IService<Channel> {

    Channel createChannel(ChannelRequest request);

    void updateChannel(Long id, ChannelRequest request);

    /** 更新渠道启用状态,并同步能力表 */
    void updateStatus(Long id, Integer status);

    /** 删除渠道,并清理其能力行 */
    void deleteChannel(Long id);

    /**
     * 为指定 (分组, 模型) 挑选一个可用渠道:走能力表,先按优先级取最高,再在同优先级内按权重随机。
     * 无可用渠道返回 null。
     */
    Channel selectChannelForModel(String group, String model);
}
