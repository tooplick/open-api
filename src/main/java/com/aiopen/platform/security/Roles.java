package com.aiopen.platform.security;

/**
 * 角色常量,取值与库中 user.role 列一致(小写无前缀)。
 * Spring Security 的权限名在 {@link AuthUser#getAuthorities()} 中加 ROLE_ 前缀。
 */
public final class Roles {

    public static final String ADMIN = "admin";
    public static final String USER = "user";

    private Roles() {
    }
}
