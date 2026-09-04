package ru.x5.devpulse.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;

/** Возвращает daily-агрегаты за период с опциональными фильтрами по автору/команде (фильтр в БД). */
@RequiredArgsConstructor
public final class GetDailyStatsService implements GetDailyStatsUseCase {

    private final DailyStatsRepository dailyStatsRepository;

    @Override
    public List<DailyAuthorStats> findByPeriod(Period period, Optional<Email> author, Optional<String> team) {
        return dailyStatsRepository.findByPeriod(period, author, team);
    }
}
