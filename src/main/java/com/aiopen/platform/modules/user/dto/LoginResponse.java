package com.aiopen.platform.modules.user.dto;

import com.aiopen.platform.modules.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private User user;
}
