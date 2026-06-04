package ru.x5.devpulse.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import ru.x5.devpulse.domain.model.review.CollectedMergeRequest;

/**
 * Port out: сбор MR с участием ревьюеров из GitLab.
 *
 * <p>Адаптер сам резолвит GitLab-юзеров в email (public_email / {@code username@домен}),
 * фильтрует саморевью и системные заметки. Возвращает готовые к записи
 * {@link CollectedMergeRequest}.</p>
 */
public interface ReviewGateway {

    /**
     * MR, обновлённые после {@code updatedAfter}, по всем настроенным проектам.
     * Инкрементальный сбор: {@code updatedAfter} = начало периода прогона.
     */
    List<CollectedMergeRequest> fetchMergeRequests(LocalDateTime updatedAfter);
}
