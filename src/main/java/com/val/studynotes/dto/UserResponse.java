package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
/** Данные текущего пользователя для клиента: только имя, без пароля, хеша и ролей. */
@Schema(description = "Текущий пользователь")
public record UserResponse(String username) {
}
