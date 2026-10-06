package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Данные для входа")
public record LoginRequest(
        @NotBlank(message = "Имя пользователя обязательно")
        String username,
        @NotBlank(message = "Пароль обязателен")
        @Schema(description = "Пароль", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password) {

    /** Пароль не должен попасть в логи через toString. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
