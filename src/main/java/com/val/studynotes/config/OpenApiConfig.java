package com.val.studynotes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Описание контракта /api/** в OpenAPI. Документ и Swagger UI закрыты входом цепочкой безопасности
 * (отдельных исключений в {@link SecurityConfig} нет).
 */
@Configuration
public class OpenApiConfig {

    public static final String SESSION_COOKIE = "sessionCookie";
    public static final String CSRF_HEADER = "csrfHeader";

    private static final String DESCRIPTION = """
            REST API персональной системы IT-заметок. Язык описаний: русский.

            **Как войти.** Аутентификация сессионная. Сначала любой запрос к /api/** (например, GET /api/auth/me) \
            выдаёт cookie `XSRF-TOKEN`. Затем `POST /api/auth/login` с телом `{"username": "...", "password": "..."}` \
            и заголовком `X-XSRF-TOKEN` со значением этой cookie. В ответ приходит cookie сессии `JSESSIONID` \
            (HttpOnly) и обновлённая cookie `XSRF-TOKEN`; дальше браузер отправляет их сам. \
            Выход: `POST /api/auth/logout` (204).

            **CSRF.** Все небезопасные запросы (POST, PUT, DELETE) должны содержать заголовок `X-XSRF-TOKEN` \
            со значением cookie `XSRF-TOKEN`, иначе ответ 403. Cookie читается из JavaScript.

            **Ошибки.** Все ошибки /api/** приходят JSON в формате ErrorResponse. Без входа ответ 401.
            """;

    @Bean
    public OpenAPI studynotesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("studynotes API")
                        .version("v1")
                        .description(DESCRIPTION))
                .components(new Components()
                        .addSecuritySchemes(SESSION_COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Cookie сессии, выдаётся после POST /api/auth/login."))
                        .addSecuritySchemes(CSRF_HEADER, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("Значение cookie XSRF-TOKEN; обязателен для POST, PUT и DELETE.")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE));
    }

    /**
     * Небезопасным методам без явно заданной безопасности добавляется требование сессии вместе с CSRF
     * (оба в одном требовании: нужны оба). Вход описан аннотацией отдельно: ему нужен только CSRF.
     */
    @Bean
    public OperationCustomizer csrfRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getSecurity() == null && isUnsafe(handlerMethod.getMethod())) {
                operation.addSecurityItem(new SecurityRequirement()
                        .addList(SESSION_COOKIE).addList(CSRF_HEADER));
            }
            return operation;
        };
    }

    /**
     * Выход обрабатывает фильтр Spring Security, а не контроллер, поэтому springdoc его не видит:
     * операция добавляется в документ вручную (путь и ответ 204 заданы в {@link SecurityConfig}).
     */
    @Bean
    public GlobalOpenApiCustomizer logoutPathCustomizer() {
        return openApi -> {
            Operation logout = new Operation()
                    .addTagsItem("Аутентификация")
                    .summary("Выход")
                    .description("Завершает сессию и удаляет cookie JSESSIONID. Нужен заголовок X-XSRF-TOKEN. "
                            + "Повторный выход без сессии тоже даёт 204.")
                    .operationId("logout")
                    .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE).addList(CSRF_HEADER))
                    .responses(new ApiResponses()
                            .addApiResponse("204", new ApiResponse().description("Сессия завершена"))
                            .addApiResponse("403", new ApiResponse()
                                    .description("Нет или неверный токен CSRF")
                                    .content(new Content().addMediaType("application/json",
                                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))))));
            openApi.getPaths().addPathItem("/api/auth/logout", new PathItem().post(logout));
        };
    }

    private static boolean isUnsafe(java.lang.reflect.Method method) {
        return method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class);
    }
}
