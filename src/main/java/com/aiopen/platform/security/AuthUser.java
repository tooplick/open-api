package com.aiopen.platform.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 主体:承载当前登录用户的 id / 用户名 / 角色。
 * 由 {@link CustomUserDetailsService} 构造,认证后存入 SecurityContext;
 * 控制器可用 {@code @AuthenticationPrincipal AuthUser} 注入获取当前用户。
 */
@Getter
public class AuthUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String role;
    private final boolean enabled;

    public AuthUser(Long id, String username, String password, String role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
    }

    public boolean isAdmin() {
        return Roles.ADMIN.equals(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 库中 role 为小写无前缀,转成 Spring Security 约定的 ROLE_ 前缀大写形式,
        // 以便 @PreAuthorize("hasRole('ADMIN')") 生效
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
