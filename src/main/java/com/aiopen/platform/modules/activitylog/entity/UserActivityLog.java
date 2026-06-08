package com.aiopen.platform.modules.activitylog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户活动日志(登录/注册/操作审计)。不做逻辑删除,仅插入时填充 createTime。
 */
@Data
@TableName("user_activity_log")
public class UserActivityLog implements Serializable {

    @TableId
    private Long id;

    private Long userId;

    private String username;

    /** 操作类型: LOGIN, REGISTER, PASSWORD_CHANGE, APIKEY_CREATE 等 */
    private String action;

    /** 资源类型: USER / API_KEY / CHANNEL / SETTING */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 资源名称(冗余) */
    private String resourceName;

    /** 补充说明 */
    private String detail;

    private String ip;

    private String userAgent;

    /** 1成功 2失败 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
