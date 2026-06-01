package com.aiopen.platform.modules.channel.service;

import com.aiopen.platform.modules.channel.dto.ChannelRequest;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ChannelService extends IService<Channel> {

    Channel createChannel(ChannelRequest request);

    void updateChannel(Long id, ChannelRequest request);

    /**
     * 为指定模型挑选一个可用渠道:先按优先级取最高,再在同优先级内按权重随机。
     * 无可用渠道返回 null。
     */
    Channel selectChannelForModel(String model);
}
