package com.val.studynotes.testsupport;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * csrf() и formLogin() из spring-security-test при первом использовании заменяют поле tokenRepository в бине CsrfFilter
 * на обёртку над HttpSessionCsrfTokenRepository (исходный CookieCsrfTokenRepository отбрасывается) и нигде не возвращают.
 * Контекст Spring кешируется между классами, поэтому после таких тестов cookie XSRF-TOKEN в нём больше не выдаётся.
 * Расширение запоминает репозиторий до теста и возвращает его после, чтобы порядок тестов и классов не влиял на результат.
 * Подключать к тестовым классам, где используются csrf() или formLogin().
 */
public class RestoreCsrfTokenRepository implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(RestoreCsrfTokenRepository.class);
    private static final String KEY = "original";
    private static final String FIELD = "tokenRepository";

    @Override
    public void beforeEach(ExtensionContext context) {
        CsrfFilter filter = csrfFilter(context);
        if (filter != null) {
            context.getStore(NAMESPACE).put(KEY, ReflectionTestUtils.getField(filter, FIELD));
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        CsrfTokenRepository original = context.getStore(NAMESPACE).remove(KEY, CsrfTokenRepository.class);
        CsrfFilter filter = csrfFilter(context);
        if (original != null && filter != null) {
            ReflectionTestUtils.setField(filter, FIELD, original);
        }
    }

    private static CsrfFilter csrfFilter(ExtensionContext context) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        if (!applicationContext.containsBean("springSecurityFilterChain")) {
            return null;
        }
        Filter chain = applicationContext.getBean("springSecurityFilterChain", Filter.class);
        if (!(chain instanceof FilterChainProxy proxy)) {
            return null;
        }
        return proxy.getFilterChains().stream()
                .flatMap(c -> c.getFilters().stream())
                .filter(CsrfFilter.class::isInstance)
                .map(CsrfFilter.class::cast)
                .findFirst().orElse(null);
    }
}
