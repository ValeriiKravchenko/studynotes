package com.val.studynotes.config;

import com.val.studynotes.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 401/403 в формате {@link ErrorResponse} для /api/**; сообщения фиксированные, без данных запроса и деталей токена. */
final class ApiSecurityErrorHandlers {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ApiSecurityErrorHandlers() {
    }

    static AuthenticationEntryPoint unauthorized() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) ->
                write(response, HttpStatus.UNAUTHORIZED, "Требуется аутентификация");
    }

    static AccessDeniedHandler forbidden() {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) ->
                write(response, HttpStatus.FORBIDDEN, "Доступ запрещён");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(),
                new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
