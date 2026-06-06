package ru.x5.devpulse.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetPerformanceReviewUseCase;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.performance.KaitenInsights;
import ru.x5.devpulse.domain.model.performance.NotableResults;
import ru.x5.devpulse.domain.model.performance.PerformanceMetrics;
import ru.x5.devpulse.domain.model.performance.PerformanceReview;
import ru.x5.devpulse.domain.model.performance.PeriodMetrics;
import ru.x5.devpulse.domain.model.performance.TaskTypeBreakdown;
import ru.x5.devpulse.domain.model.review.ReviewAuthorStats;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;
import ru.x5.devpulse.domain.service.PerformanceReviewAssembler;
import ru.x5.devpulse.domain.service.ReviewSummarizer;

/**
 * Досье к perf-review: композиция уже собранных данных, без отдельного сбора.
 *
 * <p><b>Источники:</b> git-агрегаты ({@link DailyStatsRepository}) и ревью-метрики
 * ({@link ReviewStatsRepository} + {@link ReviewSummarizer}) — историчны, считаются для
 * текущего и (опционально) предыдущего периода → дельты. Карточки Kaiten ({@link KaitenGateway})
 * тянутся live только за текущий период — снапшот «как сейчас», без дельт (см. future.md).</p>
 *
 * <p>Вся арифметика (дельты, счёт карточек, заметные результаты) — в чистом доменном
 * {@link PerformanceReviewAssembler}; здесь только оркестрация портов.</p>
 */
@RequiredArgsConstructor
public final class PerformanceReviewService implements GetPerformanceReviewUseCase {

    /** Сколько элементов показывать в каждом блоке «заметных результатов». */
    private static final int MAX_NOTABLE = 5;

    private final UnifiedUserRepository unifiedUserRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final ReviewStatsRepository reviewStatsRepository;
    private final KaitenGateway kaitenGateway;

    @Override
    public Optional<PerformanceReview> review(Email email, Period period, boolean compareToPrevious) {
        Optional<UnifiedUser> userOpt = unifiedUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        UnifiedUser user = userOpt.get();

        PeriodMetrics current = periodMetrics(email, period);
        PeriodMetrics previous = null;
        Period comparedTo = null;
        if (compareToPrevious) {
            comparedTo = period.previousAdjacent();
            previous = periodMetrics(email, comparedTo);
        }

        // Карточки — live, только текущий период (снапшот «как сейчас»). Без kaiten_id — пусто.
        List<KaitenCard> cards = user.kaiten()
                .map(kid -> kaitenGateway.fetchCardsForMember(kid, period.fromAtStartOfDay()))
                .orElseGet(List::of);

        TaskTypeBreakdown breakdown = PerformanceReviewAssembler.breakdown(cards, period);
        PerformanceMetrics metrics = PerformanceReviewAssembler.metrics(current, previous, breakdown);
        KaitenInsights kaiten = PerformanceReviewAssembler.kaitenInsights(cards, period);
        NotableResults notable =
                PerformanceReviewAssembler.notable(cards, period, kaiten.development(), MAX_NOTABLE);

        return Optional.of(new PerformanceReview(
                user, period, comparedTo, metrics, breakdown, kaiten, notable));
    }

    /** Сырые git+ревью-метрики одного человека за период. */
    private PeriodMetrics periodMetrics(Email email, Period period) {
        long commits = 0;
        long merge = 0;
        long added = 0;
        long deleted = 0;
        long test = 0;
        for (DailyAuthorStats s : dailyStatsRepository.findByAuthorAndPeriod(email, period)) {
            commits += s.commits();
            merge += s.mergeCommits();
            added += s.addedLines();
            deleted += s.deletedLines();
            test += s.testAddedLines();
        }
        ReviewAuthorStats review = reviewFor(email, period);
        return new PeriodMetrics(
                commits, Math.max(0, commits - merge), added, deleted, test,
                review.reviewsGiven(), review.commentsGiven(), review.reviewsReceived(),
                review.avgTimeToMergeHours(), review.mergedMrCount());
    }

    /** Ревью-метрики конкретного человека за период (per-user срез из общей агрегации). */
    private ReviewAuthorStats reviewFor(Email email, Period period) {
        List<ReviewAuthorStats> all =
                ReviewSummarizer.summarize(reviewStatsRepository.findMergeRequestsByPeriod(period));
        for (ReviewAuthorStats a : all) {
            if (a.email().equals(email)) {
                return a;
            }
        }
        // Нет ревью-активности за период — нули.
        return new ReviewAuthorStats(email, null, null, 0, 0, 0, 0.0, 0, null, false);
    }
}
