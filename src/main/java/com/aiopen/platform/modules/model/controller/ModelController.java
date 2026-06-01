package com.aiopen.platform.modules.model.controller;

import com.aiopen.platform.common.result.PageResult;
import com.aiopen.platform.common.result.Result;
import com.aiopen.platform.modules.model.dto.ModelRequest;
import com.aiopen.platform.modules.model.entity.Model;
import com.aiopen.platform.modules.model.service.ModelService;
import com.aiopen.platform.security.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型管理。查询对所有登录用户开放,增删改仅管理员。
 */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    @GetMapping("/page")
    public Result<PageResult<Model>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String modelName) {
        Page<Model> page = modelService.page(new Page<>(current, size),
                Wrappers.<Model>lambdaQuery()
                        .like(StringUtils.hasText(modelName), Model::getModelName, modelName)
                        .orderByAsc(Model::getModelName));
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/enabled")
    public Result<List<Model>> enabled() {
        return Result.success(modelService.listEnabled());
    }

    @PostMapping
    public Result<Model> create(@Valid @RequestBody ModelRequest request) {
        UserContext.requireAdmin();
        return Result.success(modelService.createModel(request));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ModelRequest request) {
        UserContext.requireAdmin();
        modelService.updateModel(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserContext.requireAdmin();
        modelService.removeById(id);
        return Result.success();
    }
}
