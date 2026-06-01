package com.aiopen.platform.modules.model.service;

import com.aiopen.platform.modules.model.dto.ModelRequest;
import com.aiopen.platform.modules.model.entity.Model;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ModelService extends IService<Model> {

    Model createModel(ModelRequest request);

    void updateModel(Long id, ModelRequest request);

    /** relay 计费用:按模型名查询 */
    Model getByName(String modelName);

    /** 所有启用的模型 */
    List<Model> listEnabled();
}
