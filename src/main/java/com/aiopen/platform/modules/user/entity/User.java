package com.aiopen.platform.modules.user.entity;

import com.aiopen.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    @TableId
    private Long id;

    private String username;

    /** BCrypt 密文,不对外序列化 */
    @JsonIgnore
    private String password;

    private String email;

    /** admin / user */
    private String role;

    /** 1启用 0禁用 */
    private Integer status;

    /** 总额度(点数) */
    private Long quota;

    /** 已用额度(点数) */
    private Long usedQuota;
}
