package com.aiopen.platform.security;

import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 按用户名从数据库加载用户,供 Spring Security 认证使用:
 * 登录时由 AuthenticationManager 调用,后续请求由 {@link JwtAuthenticationFilter} 调用。
 * 每次请求实时查库,被禁用/删除的账号会立即失效。
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.getByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        boolean enabled = user.getStatus() != null && user.getStatus() == 1;
        return new AuthUser(user.getId(), user.getUsername(), user.getPassword(), user.getRole(), enabled);
    }
}
