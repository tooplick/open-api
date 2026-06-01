package com.aiopen.platform.modules.channel.controller;

import com.aiopen.platform.common.result.PageResult;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.channel.dto.ChannelRequest;
import com.aiopen.platform.modules.channel.dto.FetchModelsRequest;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.channel.service.ChannelService;
import com.aiopen.platform.security.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 渠道管理(仅管理员)。
 */
@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping("/page")
    public Result<PageResult<Channel>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String name) {
        UserContext.requireAdmin();
        Page<Channel> page = channelService.page(new Page<>(current, size),
                Wrappers.<Channel>lambdaQuery()
                        .like(StringUtils.hasText(name), Channel::getName, name)
                        .orderByDesc(Channel::getPriority)
                        .orderByDesc(Channel::getId));
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    public Result<Channel> get(@PathVariable Long id) {
        UserContext.requireAdmin();
        return Result.success(channelService.getById(id));
    }

    @PostMapping
    public Result<Channel> create(@Valid @RequestBody ChannelRequest request) {
        UserContext.requireAdmin();
        return Result.success(channelService.createChannel(request));
    }

    @PostMapping("/fetch-models")
    public Result<List<String>> fetchModels(@Valid @RequestBody FetchModelsRequest request) {
        UserContext.requireAdmin();
        return Result.success(channelService.fetchUpstreamModels(request));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ChannelRequest request) {
        UserContext.requireAdmin();
        channelService.updateChannel(id, request);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        UserContext.requireAdmin();
        channelService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserContext.requireAdmin();
        channelService.deleteChannel(id);
        return Result.success();
    }
}
