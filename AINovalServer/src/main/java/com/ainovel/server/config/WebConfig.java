package com.ainovel.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

import com.ainovel.server.security.CurrentUserMethodArgumentResolver ;

/**
 * WebFlux配置 用于配置参数解析器、跨域、静态资源等
 */
@Configuration
@EnableWebFlux
public class WebConfig implements WebFluxConfigurer {

    private final CurrentUserMethodArgumentResolver currentUserResolver;

    @Autowired
    public WebConfig(CurrentUserMethodArgumentResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(currentUserResolver);
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 长期缓存：WASM、字体、图标等不常变化的大文件（缓存1年）
        CacheControl longTermCache = CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic();
        
        // 中期缓存：JS、CSS 等可能更新的文件（缓存1天，需要重新验证）
        CacheControl mediumCache = CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic().mustRevalidate();
        
        // 短期缓存：HTML 等入口文件（缓存5分钟）
        CacheControl shortCache = CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic().mustRevalidate();
        
        // CanvasKit WASM 文件 - 长期缓存（约1.5MB，很少变化）
        registry.addResourceHandler("/canvaskit/**")
                .addResourceLocations("file:/app/web/canvaskit/")
                .setCacheControl(longTermCache)
                .resourceChain(true);
        
        // 字体文件 - 长期缓存
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("file:/app/web/fonts/", "file:/app/web/assets/fonts/")
                .setCacheControl(longTermCache)
                .resourceChain(true);
        
        registry.addResourceHandler("/assets/fonts/**")
                .addResourceLocations("file:/app/web/assets/fonts/")
                .setCacheControl(longTermCache)
                .resourceChain(true);
        
        // 图标文件 - 长期缓存
        registry.addResourceHandler("/icons/**")
                .addResourceLocations("file:/app/web/icons/")
                .setCacheControl(longTermCache)
                .resourceChain(true);
        
        // 管理员面板静态资源
        registry.addResourceHandler("/admin/canvaskit/**")
                .addResourceLocations("file:/app/admin_web/canvaskit/")
                .setCacheControl(longTermCache)
                .resourceChain(true);
        
        registry.addResourceHandler("/admin/assets/fonts/**")
                .addResourceLocations("file:/app/admin_web/assets/fonts/")
                .setCacheControl(longTermCache)
                .resourceChain(true);
        
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("file:/app/admin_web/")
                .setCacheControl(mediumCache)
                .resourceChain(true);
        
        // JS/CSS 文件 - 中期缓存
        registry.addResourceHandler("/*.js", "/*.css")
                .addResourceLocations("file:/app/web/")
                .setCacheControl(mediumCache)
                .resourceChain(true);
        
        // 其他资源文件 - 中期缓存
        registry.addResourceHandler("/assets/**", "/shaders/**", "/packages/**")
                .addResourceLocations("file:/app/web/assets/", "file:/app/web/shaders/", "file:/app/web/packages/")
                .setCacheControl(mediumCache)
                .resourceChain(true);
        
        // HTML 入口文件 - 短期缓存
        registry.addResourceHandler("/", "/index.html")
                .addResourceLocations("file:/app/web/")
                .setCacheControl(shortCache)
                .resourceChain(true);
        
        // Manifest 和配置文件 - 短期缓存
        registry.addResourceHandler(
                "/manifest.json",
                "/AssetManifest.json",
                "/AssetManifest.bin",
                "/AssetManifest.bin.json",
                "/FontManifest.json",
                "/flutter.js",
                "/flutter_bootstrap.js",
                "/*.json"
        )
                .addResourceLocations("file:/app/web/")
                .setCacheControl(shortCache)
                .resourceChain(true);
        
        // 图标和 favicon - 中期缓存
        registry.addResourceHandler("/Icon-192.png", "/Icon-512.png", "/favicon.ico", "/favicon.png")
                .addResourceLocations("file:/app/web/")
                .setCacheControl(mediumCache)
                .resourceChain(true);
    }
}
