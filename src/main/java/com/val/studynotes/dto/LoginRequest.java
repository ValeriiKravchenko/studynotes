package com.val.studynotes.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Имя пользователя обязательно")
        String username,
        @NotBlank(message = "Пароль обязателен")
        String password) {

    /** Пароль не должен попасть в логи через toString. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
