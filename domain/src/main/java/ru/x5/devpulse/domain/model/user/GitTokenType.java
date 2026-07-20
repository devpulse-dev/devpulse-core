package ru.x5.devpulse.domain.model.user;

/**
 * Тип токена для обращения к GitLab от имени пользователя. Различие — только в заголовке
 * на стороне адаптера: {@code PAT → PRIVATE-TOKEN}, {@code OAUTH → Authorization: Bearer}.
 */
public enum GitTokenType {
    PAT,
    OAUTH
}
