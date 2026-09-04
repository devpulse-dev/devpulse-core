package ru.x5.devpulse.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetHourlyStatsUseCase;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.HourlyStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Почасовая статистика. Агрегацию (GROUP BY день/час/автор) с опциональными фильтрами
 * автора/команды делает БД через {@link CommitRepository#aggregateHourly} — не
 * поднимаем коммиты в память.
 *
 * <p>Enrichment здесь по-прежнему не нужен: ячейка несёт email автора, а имена, аватары
 * и команды клиент подставляет из уже загруженного справочника пользователей — иначе
 * ради подписи под heatmap пришлось бы джойнить {@code unified_user} на каждую из
 * 7×24 ячеек.</p>
 */
@RequiredArgsConstructor
public final class GetHourlyStatsService implements GetHourlyStatsUseCase {

    private final CommitRepository commitRepository;

    @Override
    public HourlyStats get(Period period, Optional<Email> author, Optional<String> team) {
        return new HourlyStats(period, commitRepository.aggregateHourly(period, author, team));
    }
}
