package com.val.studynotes.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.function.Supplier;

/**
 * SPA шлёт сырое значение cookie XSRF-TOKEN в заголовке X-XSRF-TOKEN. Замаскированный вариант токена
 * разбирается для совместимости: сначала значение читается как замаскированное, при неудаче как сырое;
 * итоговое сравнение с токеном делает CsrfFilter.
 */
final class CombinedCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String value = xor.resolveCsrfTokenValue(request, csrfToken);
        return value != null ? value : plain.resolveCsrfTokenValue(request, csrfToken);
    }
}
