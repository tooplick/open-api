package com.aiopen.platform.modules.ability.service.impl;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aiopen.platform.modules.ability.entity.Ability;
import com.aiopen.platform.modules.ability.mapper.AbilityMapper;
import com.aiopen.platform.modules.ability.service.AbilityService;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service
public class AbilityServiceImpl extends ServiceImpl<AbilityMapper, Ability> implements AbilityService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildForChannel(Channel channel) {
        deleteForChannel(channel.getId());
        List<String> models = splitCsv(channel.getModels());
        List<String> groups = splitCsv(channel.getGroup());
        if (models.isEmpty() || groups.isEmpty()) {
            return;
        }
        int enabled = (channel.getStatus() != null && channel.getStatus() == 1) ? 1 : 0;
        int priority = channel.getPriority() == null ? 0 : channel.getPriority();
        int weight = channel.getWeight() == null ? 1 : channel.getWeight();
        List<Ability> abilities = new ArrayList<>();
        for (String g : groups) {
            for (String m : models) {
                Ability a = new Ability();
                a.setGroup(g);
                a.setModel(m);
                a.setChannelId(channel.getId());
                a.setEnabled(enabled);
                a.setPriority(priority);
                a.setWeight(weight);
                abilities.add(a);
            }
        }
        saveBatch(abilities);
    }

    @Override
    public void deleteForChannel(Long channelId) {
        if (channelId == null) {
            return;
        }
        remove(Wrappers.<Ability>lambdaQuery().eq(Ability::getChannelId, channelId));
    }

    @Override
    public Long selectChannelId(String group, String model) {
        if (!StringUtils.hasText(group) || !StringUtils.hasText(model)) {
            return null;
        }
        List<Ability> list = list(Wrappers.<Ability>lambdaQuery()
                .eq(Ability::getGroup, group)
                .eq(Ability::getModel, model)
                .eq(Ability::getEnabled, 1));
        if (list.isEmpty()) {
            return null;
        }
        int maxPriority = list.stream()
                .mapToInt(this::priorityOf)
                .max()
                .orElse(0);
        List<Ability> top = list.stream()
                .filter(a -> priorityOf(a) == maxPriority)
                .collect(Collectors.toList());
        return weightedPick(top).getChannelId();
    }

    @Override
    public List<String> distinctModels() {
        return list(Wrappers.<Ability>lambdaQuery().eq(Ability::getEnabled, 1))
                .stream()
                .map(Ability::getModel)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> distinctModels(String group) {
        if (!StringUtils.hasText(group)) {
            return distinctModels();
        }
        return list(Wrappers.<Ability>lambdaQuery()
                .eq(Ability::getGroup, group)
                .eq(Ability::getEnabled, 1))
                .stream()
                .map(Ability::getModel)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private int priorityOf(Ability a) {
        return a.getPriority() == null ? 0 : a.getPriority();
    }

    private int weightOf(Ability a) {
        int w = a.getWeight() == null ? 1 : a.getWeight();
        return Math.max(1, w);
    }

    private Ability weightedPick(List<Ability> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        int totalWeight = candidates.stream().mapToInt(this::weightOf).sum();
        int r = RANDOM.nextInt(totalWeight);
        int acc = 0;
        for (Ability a : candidates) {
            acc += weightOf(a);
            if (r < acc) {
                return a;
            }
        }
        return candidates.get(0);
    }

    private List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}
