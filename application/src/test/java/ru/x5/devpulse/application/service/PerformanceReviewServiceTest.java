package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.performance.PerformanceReview;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerformanceReviewService (досье к perf-review)")
class PerformanceReviewServiceTest {

    private static final Email EMAIL = new Email("boris@x5.ru");
    private static final KaitenUserId KID = new KaitenUserId(7L);
    private static final Period Q1 = new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    @Mock private UnifiedUserRepository unifiedUserRepository;
    @Mock private DailyStatsRepository dailyStatsRepository;
    @Mock private ReviewStatsRepository reviewStatsRepository;
    @Mock private KaitenGateway kaitenGateway;

    private PerformanceReviewService service() {
        return new PerformanceReviewService(
                unifiedUserRepository, dailyStatsRepository, reviewStatsRepository, kaitenGateway);
    }

    @Test
    @DisplayName("Пользователя нет в unified_user → Optional.empty, в Kaiten/stats не ходим")
    void emptyWhenUserNotFound() {
        when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        Optional<PerformanceReview> result = service().review(EMAIL, Q1, true);

        assertAll("нет пользователя",
                () -> assertThat(result).isEmpty(),
                () -> verify(dailyStatsRepository, never()).findByAuthorAndPeriod(any(), any()),
                () -> verify(kaitenGateway, never()).fetchCardsForMember(any(), any()));
    }

    @Test
    @DisplayName("Без сравнения: метрики текущего периода, comparedTo = null, previous = null")
    void composesCurrentOnly() {
        when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(KID)));
        when(dailyStatsRepository.findByAuthorAndPeriod(eq(EMAIL), eq(Q1)))
                .thenReturn(List.of(daily(10, 2, 300), daily(5, 0, 100)));
        when(reviewStatsRepository.findMergeRequestsByPeriod(any())).thenReturn(List.of());
        when(kaitenGateway.fetchCardsForMember(eq(KID), any()))
                .thenReturn(List.of(defectDone(), devInProgress()));

        PerformanceReview r = service().review(EMAIL, Q1, false).orElseThrow();

        assertAll("текущий период",
                () -> assertThat(r.comparedTo()).as("без сравнения").isNull(),
                () -> assertThat(r.metrics().commits().current()).isEqualTo(15.0),
                () -> assertThat(r.metrics().commits().previous()).as("дельты нет").isNull(),
                () -> assertThat(r.metrics().nonMergeCommits().current()).isEqualTo(13.0), // 15 - 2 merge
                () -> assertThat(r.taskBreakdown().defect().done()).isEqualTo(1),
                () -> assertThat(r.taskBreakdown().development().inProgress()).isEqualTo(1),
                () -> verify(dailyStatsRepository, never())
                        .findByAuthorAndPeriod(eq(EMAIL), eq(Q1.previousAdjacent())));
    }

    @Test
    @DisplayName("Со сравнением: тянет предыдущий период и считает дельту")
    void computesDeltaWhenComparing() {
        Period prev = Q1.previousAdjacent();
        when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(KID)));
        when(dailyStatsRepository.findByAuthorAndPeriod(eq(EMAIL), eq(Q1)))
                .thenReturn(List.of(daily(20, 0, 400)));
        when(dailyStatsRepository.findByAuthorAndPeriod(eq(EMAIL), eq(prev)))
                .thenReturn(List.of(daily(10, 0, 200)));
        when(reviewStatsRepository.findMergeRequestsByPeriod(any())).thenReturn(List.of());
        when(kaitenGateway.fetchCardsForMember(eq(KID), any())).thenReturn(List.of());

        PerformanceReview r = service().review(EMAIL, Q1, true).orElseThrow();

        assertAll("дельта к прошлому периоду",
                () -> assertThat(r.comparedTo()).isEqualTo(prev),
                () -> assertThat(r.metrics().commits().current()).isEqualTo(20.0),
                () -> assertThat(r.metrics().commits().previous()).isEqualTo(10.0),
                () -> assertThat(r.metrics().commits().delta()).isEqualTo(10.0),
                () -> assertThat(r.metrics().commits().deltaPct()).isEqualTo(100.0));
    }

    @Test
    @DisplayName("Пользователь без kaiten_id → карточки не тянем, breakdown пустой")
    void noCardsWhenNoKaitenId() {
        when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(null)));
        when(dailyStatsRepository.findByAuthorAndPeriod(eq(EMAIL), eq(Q1))).thenReturn(List.of());
        when(reviewStatsRepository.findMergeRequestsByPeriod(any())).thenReturn(List.of());

        PerformanceReview r = service().review(EMAIL, Q1, false).orElseThrow();

        assertAll("нет kaiten_id",
                () -> assertThat(r.taskBreakdown().defect().total()).isZero(),
                () -> assertThat(r.taskBreakdown().development().total()).isZero(),
                () -> assertThat(r.highlights()).isEmpty(),
                () -> verify(kaitenGateway, never()).fetchCardsForMember(any(), any()));
    }

    private static UnifiedUser user(KaitenUserId kaitenId) {
        LocalDateTime now = LocalDateTime.now();
        return new UnifiedUser(1L, EMAIL, "boris", "Boris", null,
                kaitenId, null, "Platform", false, now, now, now);
    }

    private static DailyAuthorStats daily(long commits, long merge, long added) {
        return new DailyAuthorStats(null, EMAIL, LocalDate.of(2026, 1, 10), new RepoName("repo"),
                commits, merge, added, 0, 0, LocalDateTime.now(), 1L);
    }

    private static KaitenCard defectDone() {
        LocalDateTime closed = LocalDateTime.of(2026, 2, 10, 12, 0);
        return new KaitenCard(new KaitenCardId(1L), "defect", null, 8, 3,
                "col", "board", "space", null, null,
                LocalDateTime.of(2026, 1, 5, 10, 0), closed, closed, false, "https://k/1", List.of());
    }

    private static KaitenCard devInProgress() {
        return new KaitenCard(new KaitenCardId(2L), "feature", null, 70, 2,
                "col", "board", "space", null, null,
                LocalDateTime.of(2026, 1, 5, 10, 0), LocalDateTime.of(2026, 2, 1, 10, 0),
                null, false, "https://k/2", List.of());
    }
}
