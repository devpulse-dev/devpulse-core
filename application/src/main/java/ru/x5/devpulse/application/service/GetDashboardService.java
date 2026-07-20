package ru.x5.devpulse.application.service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetDashboardUseCase;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Page;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.ActivityScore;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.Dashboard;
import ru.x5.devpulse.domain.service.ActivityScorer;

/**
 * Дашборд с {@link ActivityScore} и пагинацией.
 *
 * <p><b>Pipeline:</b></p>
 * <ol>
 *   <li>Тянем daily-stats за период.</li>
 *   <li>Aggregate → плоский список {@link AuthorSummary} (без сортировки).</li>
 *   <li>Считаем {@link ActivityScore} для каждого автора (volume × quality factor).
 *       Базовый {@code expectedCommits} масштабируется под длину периода.</li>
 *   <li>Сортируем по {@code score desc}, tiebreak по email.</li>
 *   <li>Pagination → отрезаем только нужную страницу.</li>
 *   <li>Enrichment ({@code displayName}/{@code avatarUrl}) ТОЛЬКО для текущей страницы.</li>
 * </ol>
 */
@RequiredArgsConstructor
public final class GetDashboardService implements GetDashboardUseCase {

    private final DailyStatsRepository dailyStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;
    /** Baseline: ожидаемое число не-мердж коммитов за {@code ActivityScorer.BASELINE_PERIOD_DAYS} дней. */
    private final double expectedCommitsPer30Days;

    @Override
    public Dashboard get(Period period, PageRequest page) {
        // Агрегация по автору выполняется в БД (GROUP BY): в heap приходит по строке на человека,
        // а не все daily-строки периода. Scoring/сортировка/пагинация ниже работают на этом
        // компактном списке (десятки–сотни авторов), а не на миллионах daily-строк за год.
        List<AuthorSummary> authors = dailyStatsRepository.aggregateAuthorsByPeriod(period);

        if (authors.isEmpty()) {
            return new Dashboard(period, new Page<>(List.of(), page.page(), page.size(), 0));
        }

        double expected = scaleExpected(period);
        List<AuthorSummary> scored = withScores(authors, expected);
        scored.sort(byScoreDesc());

        Page<AuthorSummary> raw = Page.of(scored, page);
        List<AuthorSummary> enriched = new AuthorSummaryEnricher(unifiedUserRepository)
                .enrich(raw.items());

        return new Dashboard(period,
                new Page<>(enriched, page.page(), page.size(), raw.totalElements()));
    }

    /**
     * Масштабирует baseline под длину запрошенного периода (формула — в доменном
     * {@link ActivityScorer#scaleExpectedForDays}, единый дом с когортами).
     * Период включительный: {@code [from..to]} = (to − from) + 1 день.
     */
    private double scaleExpected(Period period) {
        long days = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
        return ActivityScorer.scaleExpectedForDays(expectedCommitsPer30Days, days);
    }

    private static List<AuthorSummary> withScores(List<AuthorSummary> authors, double expected) {
        List<AuthorSummary> result = new ArrayList<>(authors.size());
        for (AuthorSummary a : authors) {
            ActivityScore score = ActivityScorer.score(a, expected);
            result.add(a.withActivity(score));
        }
        return result;
    }

    private static Comparator<AuthorSummary> byScoreDesc() {
        return Comparator.comparingDouble((AuthorSummary a) -> a.activity().score())
                .reversed()
                .thenComparing(a -> a.email().value());
    }
}
