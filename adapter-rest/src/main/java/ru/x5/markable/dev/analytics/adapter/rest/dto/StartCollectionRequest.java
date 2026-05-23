package ru.x5.markable.dev.analytics.adapter.rest.dto;

import java.time.LocalDateTime;

/**
 * Тело {@code POST /api/v2/collection/runs}.
 * Все поля опциональны: при {@code since=null} use case подхватит точку из последнего успешного прогона.
 */
public record StartCollectionRequest(LocalDateTime since) {
}
