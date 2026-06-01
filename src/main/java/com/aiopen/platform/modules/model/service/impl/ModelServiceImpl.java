package com.aiopen.platform.modules.model.service.impl;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.modules.model.dto.ModelRequest;
import com.aiopen.platform.modules.model.entity.Model;
import com.aiopen.platform.modules.model.mapper.ModelMapper;
import com.aiopen.platform.modules.model.service.ModelService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelServiceImpl extends ServiceImpl<ModelMapper, Model> implements ModelService {

    @Override
    public Model createModel(ModelRequest request) {
        if (getByName(request.getModelName()) != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "模型已存在: " + request.getModelName());
        }
        Model model = new Model();
        BeanUtils.copyProperties(request, model);
        save(model);
        return model;
    }

    @Override
    public void updateModel(Long id, ModelRequest request) {
        Model model = new Model();
        BeanUtils.copyProperties(request, model);
        model.setId(id);
        updateById(model);
    }

    @Override
    public Model getByName(String modelName) {
        return getOne(Wrappers.<Model>lambdaQuery().eq(Model::getModelName, modelName), false);
    }

    @Override
    public List<Model> listEnabled() {
        return list(Wrappers.<Model>lambdaQuery().eq(Model::getStatus, 1).orderByAsc(Model::getModelName));
    }
}
