package com.val.studynotes.config;

import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@Configuration
public class SecurityConfig {

    @Value("${app.security.username}")
    private String username;

    @Value("${app.security.password}")
    private String password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher api = paths.matcher("/api/**");
        RequestMatcher apiLogout = paths.matcher(HttpMethod.POST, "/api/auth/logout");
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                )
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint(api))
                        .defaultAccessDeniedHandlerFor(ApiSecurityErrorHandlers.forbidden(), api)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").authenticated()
                        .anyRequest().permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(apiLogout)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );
        return http.build();
    }

    // SameSite=Lax для XSRF-TOKEN; Secure не задаём, он зависит от окружения
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    @Bean
    CsrfTokenRequestHandler csrfTokenRequestHandler() {
        return new CombinedCsrfTokenRequestHandler();
    }

    /** Тот же репозиторий контекста использует цепочка фильтров и AuthController (явное сохранение при входе). */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** Ротация токена CSRF при входе через API: старая cookie удаляется, новый токен выдаётся следом. */
    @Bean
    public SessionAuthenticationStrategy csrfRotationStrategy(CookieCsrfTokenRepository repository,
                                                              CsrfTokenRequestHandler handler) {
        CsrfAuthenticationStrategy strategy = new CsrfAuthenticationStrategy(repository);
        strategy.setRequestHandler(handler);
        return strategy;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /** /api/** получает 401 с JSON; остальные защищённые пути (Swagger UI/docs) — на корень SPA. */
    private static AuthenticationEntryPoint entryPoint(RequestMatcher api) {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> map = new LinkedHashMap<>();
        map.put(api, ApiSecurityErrorHandlers.unauthorized());
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(map);
        delegating.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/"));
        return delegating;
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
