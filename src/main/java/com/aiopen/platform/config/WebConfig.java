package com.aiopen.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web 配置:前后端合并部署时托管 Vue 构建产物。
 * CORS 与控制台接口认证已迁移到 Spring Security(见 {@code SecurityConfig});
 * relay 接口(/v1、/anthropic)有独立的 API Key 鉴权。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

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
                        // 其余未命中路径回退到前端入口;若前端未打包进 static(例如本地只跑
                        // 后端、前端交给 Vite),index.html 不存在则返回 null 走正常 404,
                        // 避免 ResourceHttpRequestHandler 解析不存在的资源抛 FileNotFoundException
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
