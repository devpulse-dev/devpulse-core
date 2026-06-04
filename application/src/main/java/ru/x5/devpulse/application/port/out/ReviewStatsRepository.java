package ru.x5.devpulse.application.port.out;

import java.util.List;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.MergeRequest;

/**
 * Port out: чтение MR с участием ревьюеров для агрегации ревью-метрик.
 *
 * <p>Запись MR/ревью — задача collection-фазы (сбор из GitLab), отдельный порт.
 * Здесь только read-side для {@code GET /stats/reviews}.</p>
 */
public interface ReviewStatsRepository {

    /**
     * MR, открытые в указанном периоде (по {@code created_at}), вместе с участием
     * ревьюеров. Период — закрытый интервал дат.
     */
    List<MergeRequest> findMergeRequestsByPeriod(Period period);
}
