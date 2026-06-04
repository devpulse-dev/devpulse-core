package ru.x5.devpulse.application.port.in;

import java.time.LocalDateTime;

/**
 * Фаза сбора ревью: тянет MR/approvals/notes из GitLab и пишет в БД.
 * Часть прогона {@code POST /api/v2/collection/runs}, изолирована от git/kaiten-фаз.
 */
public interface CollectReviewsUseCase {

    /** Собирает MR, обновлённые после {@code since}. */
    void collect(LocalDateTime since);
}
