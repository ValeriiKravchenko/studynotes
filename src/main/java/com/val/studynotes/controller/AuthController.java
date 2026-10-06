package com.val.studynotes.controller;

import com.val.studynotes.dto.ErrorResponse;
import com.val.studynotes.dto.LoginRequest;
import com.val.studynotes.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy csrfRotationStrategy;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy csrfRotationStrategy) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfRotationStrategy = csrfRotationStrategy;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest login,
                                   HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        } catch (AuthenticationException ex) {
            // Одинаковый ответ для неизвестного пользователя и неверного пароля; текст исключения не раскрывается
            ErrorResponse error = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase(), "Неверное имя пользователя или пароль");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        // Защита от фиксации сессии: идентификатор меняется, если сессия уже была
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        // Ротация токена CSRF; новый токен читается, чтобы cookie XSRF-TOKEN ушла в этом же ответе
        csrfRotationStrategy.onAuthentication(authentication, request, response);
        Object csrf = request.getAttribute(CsrfToken.class.getName());
        if (csrf instanceof CsrfToken token) {
            token.getToken();
        }

        // Автосохранения контекста в сессию нет, сохраняем явно
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(new UserResponse(authentication.getName()));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return new UserResponse(authentication.getName());
    }
}
