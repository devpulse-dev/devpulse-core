package ru.x5.devpulse.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
     * Стримит MR, обновлённые после {@code updatedAfter}, по всем настроенным проектам — отдавая
     * их <b>батчами по проекту</b> в {@code projectBatchHandler} (по образцу {@code streamCommits}/
     * {@code streamCards}).
     *
     * <p>Инкрементальный сбор: {@code updatedAfter} = начало периода прогона. Стриминг per-project,
     * чтобы не держать в heap весь корпус MR всех проектов одновременно (на бэкфилле за год это
     * OOM-риск): собрали проект → отдали батч на запись → освободили. Батч одного проекта может
     * быть пустым — обработчик сам решает, писать ли (обычно пропускает пустой).</p>
     *
     * <p>Устойчивость к сбоям — на адаптере: падение/дедлайн одного проекта не срывает остальные
     * (потери логируются агрегатно), обработчику отдаётся то, что удалось собрать.</p>
     *
     * @param cancelled опрашивается между проектами; при отмене адаптер прекращает опрашивать
     *                  новые проекты и возвращает управление. {@code BooleanSupplier} (а не
     *                  {@code CancellationSignal} из port.in), чтобы port.out не зависел от port.in
     */
    void streamMergeRequests(LocalDateTime updatedAfter,
                             BooleanSupplier cancelled,
                             Consumer<List<CollectedMergeRequest>> projectBatchHandler);
}
