package com.aiopen.platform.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 首次登录强制修改初始账号与密码的入参。
 */
@Data
public class InitialCredentialsRequest {

    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 50, message = "用户名 3-50 位")
    private String username;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 50, message = "密码 6-50 位")
    private String newPassword;
}
