package com.aiopen.platform.modules.log.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 调用日志。不做逻辑删除,仅插入时填充 createTime。
 */
@Data
@TableName("log")
public class Log implements Serializable {

    @TableId
    private Long id;

    private Long userId;

    private String username;

    private Long apiKeyId;

    private Long channelId;

    private String channelName;

    private String modelName;

    /** 1成功 2失败 */
    private Integer type;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 耗时(毫秒) */
    private Long durationMs;

    private String requestId;

    private String ip;

    /** 摘要 / 错误信息 */
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
