package ru.x5.devpulse.adapter.kaiten.dto;

import java.util.Map;

/**
 * Тело запроса «Update card» ({@code PATCH /cards/{id}}).
 *
 * <p>Пока используем только для простановки кастомных property: формат
 * {@code {"properties": {"id_6064": true}}}. Значение property — любой JSON
 * (null/number/string/array/object), поэтому {@code Object}.</p>
 */
public record KaitenCardUpdateDto(Map<String, Object> properties) {
}
