package com.val.studynotes.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA-fallback: неизвестные GET-пути (кроме /api/** и запросов к отсутствующим файлам)
 * отдают index.html, чтобы BrowserRouter работал при перезагрузке страницы и прямом переходе.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

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
                        // /api/** всегда отвечает JSON-ошибками, а не статикой; пути с расширением —
                        // реальные файлы, отсутствие которых должно давать 404, а не index.html
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return (index.exists() && index.isReadable()) ? index : null;
                    }
                });
    }
}
