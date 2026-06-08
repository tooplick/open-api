package com.aiopen.platform.modules.channel.service.impl;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.config.OutboundHttpClientFactory;
import com.aiopen.platform.modules.ability.service.AbilityService;
import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.aiopen.platform.modules.channel.dto.ChannelRequest;
import com.aiopen.platform.modules.channel.dto.FetchModelsRequest;
import com.aiopen.platform.modules.channel.entity.Channel;
import com.aiopen.platform.modules.channel.mapper.ChannelMapper;
import com.aiopen.platform.modules.channel.service.ChannelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, Channel> implements ChannelService {

    private final AbilityService abilityService;
    private final ObjectMapper objectMapper;
    private final OutboundHttpClientFactory httpClientFactory;
    private final UserActivityLogService activityLogService;
    private final HttpServletRequest servletRequest;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Channel createChannel(ChannelRequest request) {
        if (!StringUtils.hasText(request.getApiKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写上游密钥");
        }
        Channel channel = new Channel();
        BeanUtils.copyProperties(request, channel);
        save(channel);
        abilityService.rebuildForChannel(channel);
        recordActivity("CHANNEL_CREATE", "CHANNEL", channel.getId(), channel.getName(), "创建渠道", 1);
        return channel;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChannel(Long id, ChannelRequest request) {
        Channel existing = getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "渠道不存在");
        }
        Channel channel = new Channel();
        BeanUtils.copyProperties(request, channel);
        channel.setId(id);
        // 密钥留空:库里有就沿用旧密钥,库里也没有则要求填写
        if (!StringUtils.hasText(request.getApiKey())) {
            if (!StringUtils.hasText(existing.getApiKey())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "请填写上游密钥");
            }
            channel.setApiKey(existing.getApiKey());
        }
        updateById(channel);
        abilityService.rebuildForChannel(getById(id));
        recordActivity("CHANNEL_UPDATE", "CHANNEL", id, request.getName(), "更新渠道", 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setStatus(status);
        updateById(channel);
        abilityService.rebuildForChannel(getById(id));
        recordActivity("CHANNEL_STATUS_CHANGE", "CHANNEL", id, null,
                "状态变更为 " + (status == 1 ? "启用" : "禁用"), 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChannel(Long id) {
        Channel existing = getById(id);
        String name = existing != null ? existing.getName() : null;
        removeById(id);
        abilityService.deleteForChannel(id);
        recordActivity("CHANNEL_DELETE", "CHANNEL", id, name, "删除渠道", 1);
    }

    @Override
    public Channel selectChannelForModel(String group, String model) {
        Long channelId = abilityService.selectChannelId(group, model);
        if (channelId == null) {
            return null;
        }
        return getById(channelId);
    }

    @Override
    public List<String> fetchUpstreamModels(FetchModelsRequest request) {
        String base = request.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String key = resolveKey(request);
        String url = base + "/v1/models";

        HttpResponse<String> resp;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + key)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            resp = httpClientFactory.create(Duration.ofSeconds(10), HttpClient.Version.HTTP_1_1)
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("拉取上游模型失败 url={}: {}", url, e.getMessage());
            throw new BusinessException(ResultCode.CHANNEL_REQUEST_FAILED, "连接上游失败: " + e.getMessage());
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new BusinessException(ResultCode.CHANNEL_REQUEST_FAILED,
                    "上游返回状态 " + resp.statusCode() + ",请检查地址与密钥");
        }
        return parseModelIds(resp.body());
    }

    /** 取请求密钥:优先用入参(多 key 取第一行);留空且带 id 时回退到库中原密钥。 */
    private String resolveKey(FetchModelsRequest request) {
        if (StringUtils.hasText(request.getApiKey())) {
            return firstKey(request.getApiKey());
        }
        if (request.getId() != null) {
            Channel existing = getById(request.getId());
            if (existing != null && StringUtils.hasText(existing.getApiKey())) {
                return firstKey(existing.getApiKey());
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "请先填写上游密钥再获取模型");
    }

    private String firstKey(String raw) {
        return Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(raw.trim());
    }

    /** 解析 OpenAI 兼容的 /v1/models 响应,抽取去重排序后的模型 id。 */
    private List<String> parseModelIds(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.get("data") : root;
            TreeSet<String> ids = new TreeSet<>();
            if (data != null && data.isArray()) {
                for (JsonNode m : data) {
                    String id = m.path("id").asText(null);
                    if (StringUtils.hasText(id)) {
                        ids.add(id.trim());
                    }
                }
            }
            return new ArrayList<>(ids);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.CHANNEL_REQUEST_FAILED, "解析上游模型列表失败");
        }
    }

    private void recordActivity(String action, String resourceType, Long resourceId,
                                String resourceName, String detail, int status) {
        UserActivityLog log = new UserActivityLog();
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        log.setDetail(detail);
        log.setIp(servletRequest.getRemoteAddr());
        log.setUserAgent(servletRequest.getHeader("User-Agent"));
        log.setStatus(status);
        activityLogService.record(log);
    }
}
