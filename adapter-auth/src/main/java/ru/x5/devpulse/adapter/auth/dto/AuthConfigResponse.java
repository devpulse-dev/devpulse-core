package ru.x5.devpulse.adapter.auth.dto;

/**
 * Публичная конфигурация аутентификации для фронта (`GET /auth/config`).
 *
 * @param oauthEnabled настроен ли вход через GitLab OAuth2 (есть регистрация клиента).
 *                     Фронт показывает кнопку «Войти через GitLab» только когда {@code true}.
 */
public record AuthConfigResponse(boolean oauthEnabled) {}
