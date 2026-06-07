package com.aiopen.platform.modules.ability.controller;

import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.ability.service.AbilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可用模型查询(控制台, 登录可见)。模型由各渠道的 models 字段聚合得到, 不再手动维护。
 */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final AbilityService abilityService;

    /** 所有可用模型,或指定分组下的可用模型(去重、排序) */
    @GetMapping
    public Result<List<String>> list(@RequestParam(required = false) String group) {
        return Result.success(abilityService.distinctModels(group));
    }
}
