package com.aiopen.platform.modules.apikey.entity;

import com.aiopen.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_key")
public class ApiKey extends BaseEntity {

    @TableId
    private Long id;

    private Long userId;

    private String name;

    /** sk- 开头的密钥明文 */
    private String apiKey;

    /** 1启用 0禁用 */
    private Integer status;

    /** 独立额度上限,0 表示不单独限额(跟随用户) */
    private Long quota;

    private Long usedQuota;

    /** 过期时间,null 表示永不过期 */
    private LocalDateTime expireTime;
}
