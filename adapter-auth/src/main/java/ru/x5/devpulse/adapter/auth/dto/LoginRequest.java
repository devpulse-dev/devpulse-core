package ru.x5.devpulse.adapter.auth.dto;

/** Тело {@code POST /auth/login}: GitLab PAT пользователя. */
public record LoginRequest(String token) {}
