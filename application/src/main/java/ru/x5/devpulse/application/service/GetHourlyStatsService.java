package ru.x5.devpulse.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetHourlyStatsUseCase;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.HourlyStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Почасовая статистика. Агрегацию (GROUP BY день/час) с опциональными фильтрами
 * автора/команды делает БД через {@link CommitRepository#aggregateHourly} — не
 * поднимаем коммиты в память. Enrichment не нужен: матрица анонимна (счётчики, без авторов).
 */
@RequiredArgsConstructor
public final class GetHourlyStatsService implements GetHourlyStatsUseCase {

    private final CommitRepository commitRepository;

    @Override
    public HourlyStats get(Period period, Optional<Email> author, Optional<String> team) {
        return new HourlyStats(period, commitRepository.aggregateHourly(period, author, team));
    }
}
