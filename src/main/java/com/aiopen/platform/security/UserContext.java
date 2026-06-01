package com.aiopen.platform.security;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 基于 ThreadLocal 的当前登录用户上下文(由 AuthInterceptor 填充并在请求结束时清理)。
 */
public class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String getRole() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    public static boolean isAdmin() {
        return ROLE_ADMIN.equals(getRole());
    }

    /** 要求当前用户为管理员,否则抛出无权限异常 */
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    @AllArgsConstructor
    public static class CurrentUser {
        private Long userId;
        private String username;
        private String role;
    }
}
