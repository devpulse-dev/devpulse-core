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
import ru.x5.devpulse.domain.service.StatsSummarizer;

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

    private static final int BASELINE_PERIOD_DAYS = 30;

    private final DailyStatsRepository dailyStatsRepository;
    private final UnifiedUserRepository unifiedUserRepository;
    /** Baseline: ожидаемое количество не-мердж коммитов за {@value #BASELINE_PERIOD_DAYS} дней. */
    private final double expectedCommitsPer30Days;

    @Override
    public Dashboard get(Period period, PageRequest page) {
        List<AuthorSummary> authors = StatsSummarizer.activeAuthorsByActivity(
                dailyStatsRepository.findByPeriod(period));

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
     * Масштабирует baseline 50 / 30 дней пропорционально к длине запрошенного периода.
     * Период считается включительно: {@code [from..to]} = (to − from) + 1 день.
     */
    private double scaleExpected(Period period) {
        long days = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
        return expectedCommitsPer30Days * (days / (double) BASELINE_PERIOD_DAYS);
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
