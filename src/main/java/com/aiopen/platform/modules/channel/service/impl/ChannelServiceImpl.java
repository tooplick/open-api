package com.aiopen.platform.modules.channel.service.impl;

import com.aiopen.platform.modules.channel.dto.ChannelRequest;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.channel.mapper.ChannelMapper;
import com.aiopen.platform.modules.channel.service.ChannelService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, Channel> implements ChannelService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Channel createChannel(ChannelRequest request) {
        Channel channel = new Channel();
        BeanUtils.copyProperties(request, channel);
        save(channel);
        return channel;
    }

    @Override
    public void updateChannel(Long id, ChannelRequest request) {
        Channel channel = new Channel();
        BeanUtils.copyProperties(request, channel);
        channel.setId(id);
        updateById(channel);
    }

    @Override
    public Channel selectChannelForModel(String model) {
        if (!StringUtils.hasText(model)) {
            return null;
        }
        List<Channel> enabled = list(Wrappers.<Channel>lambdaQuery().eq(Channel::getStatus, 1));
        List<Channel> matched = enabled.stream()
                .filter(c -> supportsModel(c, model))
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return null;
        }
        int maxPriority = matched.stream()
                .map(c -> c.getPriority() == null ? 0 : c.getPriority())
                .max(Comparator.naturalOrder())
                .orElse(0);
        List<Channel> candidates = matched.stream()
                .filter(c -> (c.getPriority() == null ? 0 : c.getPriority()) == maxPriority)
                .collect(Collectors.toList());
        return weightedPick(candidates);
    }

    private boolean supportsModel(Channel channel, String model) {
        if (!StringUtils.hasText(channel.getModels())) {
            return false;
        }
        Set<String> models = Arrays.stream(channel.getModels().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return models.contains(model);
    }

    private Channel weightedPick(List<Channel> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        int totalWeight = candidates.stream().mapToInt(this::effectiveWeight).sum();
        int r = RANDOM.nextInt(totalWeight);
        int acc = 0;
        for (Channel c : candidates) {
            acc += effectiveWeight(c);
            if (r < acc) {
                return c;
            }
        }
        return candidates.get(0);
    }

    private int effectiveWeight(Channel c) {
        int w = c.getWeight() == null ? 1 : c.getWeight();
        return Math.max(1, w);
    }
}
