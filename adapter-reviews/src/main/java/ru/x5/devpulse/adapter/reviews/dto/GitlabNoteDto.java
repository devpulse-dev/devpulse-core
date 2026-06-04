package ru.x5.devpulse.adapter.reviews.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Заметка (комментарий) к MR. {@code system=true} — автогенерённые события
 * («запушил коммит», «изменил описание»), их в ревью-комменты не считаем.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitlabNoteDto(
        Long id,
        GitlabUserDto author,
        boolean system
) {}
