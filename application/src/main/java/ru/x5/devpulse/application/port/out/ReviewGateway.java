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
     * их <b>чанками</b> в {@code batchHandler} (по образцу {@code streamCommits}/{@code streamCards}).
     *
     * <p>Инкрементальный сбор: {@code updatedAfter} = начало периода прогона. Чанк меньше проекта
     * и приходит по ходу его обработки, поэтому обработчик вызывается для одного проекта
     * <b>многократно</b>. Так задумано: крупный проект — это десятки тысяч MR и десятки минут
     * работы, и держать их в heap до конца проекта значит терять всё при сбое или рестарте.
     * Запись обязана быть идемпотентной (upsert по натуральному ключу) — чанк может повториться
     * при retry прогона.</p>
     *
     * <p>Устойчивость к сбоям — на адаптере: падение/дедлайн одного проекта не срывает остальные
     * (потери логируются агрегатно), обработчику отдаётся всё, что удалось собрать до сбоя.</p>
     *
     * @param cancelled опрашивается между проектами; при отмене адаптер прекращает опрашивать
     *                  новые проекты и возвращает управление. {@code BooleanSupplier} (а не
     *                  {@code CancellationSignal} из port.in), чтобы port.out не зависел от port.in
     */
    void streamMergeRequests(LocalDateTime updatedAfter,
                             BooleanSupplier cancelled,
                             Consumer<List<CollectedMergeRequest>> batchHandler);
}
