package com.aiopen.platform.config;

import com.aiopen.platform.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web 配置:CORS 与控制台接口认证拦截器。
 * 注意:relay 转发接口(/v1/**)有独立的 API Key 鉴权,不走 JWT 拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 前后端合并部署:Vue 构建产物打进 classpath:/static/,由本应用直接托管。
     * 因为前端用 history 路由,刷新/直达子路由会打到后端,这里把"未命中静态资源
     * 且非后端接口"的请求统一回退到 index.html,交给 Vue Router 处理。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 后端接口不兜底,保持其原有路由/404,避免把 JSON 接口返回成 HTML
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("v1/")
                                || resourcePath.startsWith("anthropic/")) {
                            return null;
                        }
                        // 其余未命中路径回退到前端入口
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
