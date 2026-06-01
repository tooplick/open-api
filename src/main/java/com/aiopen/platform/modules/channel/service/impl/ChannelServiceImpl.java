package com.aiopen.platform.modules.channel.service.impl;

import com.aiopen.platform.modules.ability.service.AbilityService;
import com.aiopen.platform.modules.channel.dto.ChannelRequest;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.channel.mapper.ChannelMapper;
import com.aiopen.platform.modules.channel.service.ChannelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, Channel> implements ChannelService {

    private final AbilityService abilityService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Channel createChannel(ChannelRequest request) {
        Channel channel = new Channel();
        BeanUtils.copyProperties(request, channel);
        save(channel);
        abilityService.rebuildForChannel(channel);
        return channel;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChannel(Long id, ChannelRequest request) {
        Channel channel = new Channel();
        BeanUtils.copyProperties(request, channel);
        channel.setId(id);
        updateById(channel);
        abilityService.rebuildForChannel(getById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setStatus(status);
        updateById(channel);
        abilityService.rebuildForChannel(getById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChannel(Long id) {
        removeById(id);
        abilityService.deleteForChannel(id);
    }

    @Override
    public Channel selectChannelForModel(String group, String model) {
        Long channelId = abilityService.selectChannelId(group, model);
        if (channelId == null) {
            return null;
        }
        return getById(channelId);
    }
}
