package com.val.studynotes.dto;

/** Данные текущего пользователя для клиента: только имя, без пароля, хеша и ролей. */
public record UserResponse(String username) {
}
