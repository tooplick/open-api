package com.aiopen.platform.security;

import com.aiopen.platform.config.JwtProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 控制台接口的 JWT 认证过滤器:解析 Bearer token,按用户名加载用户并写入 SecurityContext。
 * relay 接口(/v1、/anthropic)走独立的 API Key 鉴权,这里直接跳过。
 * 解析失败不在此处返回错误,交由 SecurityConfig 的 AuthenticationEntryPoint 统一处理。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/v1") || uri.startsWith("/anthropic");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(jwtProperties.getHeader());
        String prefix = jwtProperties.getPrefix();
        if (StringUtils.hasText(header) && header.startsWith(prefix)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(prefix.length()).trim();
            try {
                Claims claims = jwtUtil.parseToken(token);
                String username = claims.get(JwtUtil.CLAIM_USERNAME, String.class);
                UserDetails user = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.debug("JWT 校验失败: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
