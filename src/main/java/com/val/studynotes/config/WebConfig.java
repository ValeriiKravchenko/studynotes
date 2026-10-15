package com.val.studynotes.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA-fallback: неизвестные GET- и HEAD-пути (кроме /api, /api/** и запросов к отсутствующим файлам,
 * то есть путей с точкой) отдают index.html, чтобы BrowserRouter работал при перезагрузке страницы и прямом переходе.
 * Кэш цепочки ресурсов отключён: иначе запись создавалась бы на каждый уникальный путь.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // /api и /api/** всегда отвечают JSON-ошибками, а не статикой; пути с расширением —
                        // реальные файлы, отсутствие которых должно давать 404, а не index.html
                        if (resourcePath.equals("api") || resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return (index.exists() && index.isReadable()) ? index : null;
                    }
                });
    }
}
