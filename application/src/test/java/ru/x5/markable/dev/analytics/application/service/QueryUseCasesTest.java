package ru.x5.markable.dev.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.markable.dev.analytics.application.port.in.GetUserProfileUseCase;
import ru.x5.markable.dev.analytics.application.port.out.CommitRepository;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.KaitenCardRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.common.PageRequest;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;
import ru.x5.markable.dev.analytics.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("Query use case-ы (read-side)")
class QueryUseCasesTest {

    private static final Period PERIOD = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    private static final Email EMAIL = new Email("boris@x5.ru");
    private static final RepoName REPO = new RepoName("xrg-core");

    @Mock DailyStatsRepository dailyStatsRepository;
    @Mock CommitRepository commitRepository;
    @Mock UnifiedUserRepository unifiedUserRepository;
    @Mock KaitenCardRepository kaitenCardRepository;

    @Nested
    @DisplayName("GetDailyStatsService")
    class DailyStats {
        @Test
        @DisplayName("делегирует findByPeriod без модификаций")
        void delegatesFindByPeriod() {
            List<DailyAuthorStats> data = List.of(day(LocalDate.of(2026, 5, 10), 3));
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(data);

            assertThat(new GetDailyStatsService(dailyStatsRepository).findByPeriod(PERIOD))
                    .isSameAs(data);
        }
    }

    @Nested
    @DisplayName("GetWeeklyStatsService")
    class Weekly {
        @Test
        @DisplayName("берёт daily stats и группирует по ISO-неделям")
        void groupsByWeek() {
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(List.of(
                    day(LocalDate.of(2026, 5, 4), 1),    // ISO week 19
                    day(LocalDate.of(2026, 5, 11), 2)    // ISO week 20
            ));

            var weeks = new GetWeeklyStatsService(dailyStatsRepository).findByPeriod(PERIOD);

            assertThat(weeks).hasSize(2);
        }
    }

    @Nested
    @DisplayName("GetPeriodSummaryService")
    class Summary {
        @Test
        @DisplayName("период с пустым набором → нулевая сводка с указанным period")
        void emptyPeriod() {
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(List.of());

            var s = new GetPeriodSummaryService(dailyStatsRepository).summarize(PERIOD);

            assertAll("пустая сводка",
                    () -> assertThat(s.period()).isEqualTo(PERIOD),
                    () -> assertThat(s.totalCommits()).isZero(),
                    () -> assertThat(s.topAuthors()).isEmpty());
        }
    }

    @Nested
    @DisplayName("GetUserCommitsService")
    class UserCommits {
        @Test
        @DisplayName("прокидывает email/period/page в репозиторий 1-в-1")
        void delegatesToRepository() {
            PageRequest page = new PageRequest(0, 50);
            when(commitRepository.findByAuthor(EMAIL, PERIOD, page)).thenReturn(List.of());

            new GetUserCommitsService(commitRepository).find(EMAIL, PERIOD, page);

            verify(commitRepository).findByAuthor(EMAIL, PERIOD, page);
        }
    }

    @Nested
    @DisplayName("GetUserProfileService")
    class Profile {
        @Test
        @DisplayName("Пользователя нет в unified_user → Optional.empty(), репозитории даже не зовут")
        void unknownUser() {
            when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var result = service().findProfile(EMAIL, PERIOD);

            assertAll("пустой профиль",
                    () -> assertThat(result).isEmpty(),
                    () -> verifyNoInteractions(dailyStatsRepository),
                    () -> verifyNoInteractions(commitRepository),
                    () -> verifyNoInteractions(kaitenCardRepository));
        }

        @Test
        @DisplayName("Есть kaiten_id → дёргаем kaiten cards, AuthorSummary агрегирован из daily stats")
        void profileWithKaiten() {
            UnifiedUser user = userWithKaiten(7L);
            when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(dailyStatsRepository.findByAuthorAndPeriod(EMAIL, PERIOD)).thenReturn(List.of(
                    day(LocalDate.of(2026, 5, 1), 3),
                    day(LocalDate.of(2026, 5, 2), 2)
            ));
            when(commitRepository.findByAuthor(eq(EMAIL), eq(PERIOD), any())).thenReturn(List.of());
            when(kaitenCardRepository.findByMemberAndPeriod(new KaitenUserId(7L), PERIOD)).thenReturn(List.of());

            GetUserProfileUseCase.Profile p = service().findProfile(EMAIL, PERIOD).orElseThrow();

            assertAll("профиль с kaiten",
                    () -> assertThat(p.user()).isSameAs(user),
                    () -> assertThat(p.summary().commits()).as("3 + 2").isEqualTo(5),
                    () -> assertThat(p.summary().email()).isEqualTo(EMAIL),
                    () -> verify(kaitenCardRepository).findByMemberAndPeriod(new KaitenUserId(7L), PERIOD));
        }

        @Test
        @DisplayName("Нет kaiten_id → cards пустые, репозиторий карточек НЕ зовём")
        void profileWithoutKaiten() {
            UnifiedUser user = userWithoutKaiten();
            when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(dailyStatsRepository.findByAuthorAndPeriod(EMAIL, PERIOD)).thenReturn(List.of());
            when(commitRepository.findByAuthor(eq(EMAIL), eq(PERIOD), any())).thenReturn(List.of());

            var p = service().findProfile(EMAIL, PERIOD).orElseThrow();

            assertAll("профиль без kaiten",
                    () -> assertThat(p.cards()).isEmpty(),
                    () -> verify(kaitenCardRepository, never()).findByMemberAndPeriod(any(), any()));
        }

        private GetUserProfileService service() {
            return new GetUserProfileService(
                    unifiedUserRepository, dailyStatsRepository, commitRepository, kaitenCardRepository);
        }
    }

    /* ------------ helpers ------------ */

    private static DailyAuthorStats day(LocalDate date, long commits) {
        return new DailyAuthorStats(
                null, EMAIL, date, REPO, commits, 0, 10, 5, 1,
                LocalDateTime.now(), null);
    }

    private static UnifiedUser userWithKaiten(long kaitenId) {
        LocalDateTime now = LocalDateTime.now();
        return new UnifiedUser(1L, EMAIL, "boris", "Boris", null,
                new KaitenUserId(kaitenId), null, now, now, now);
    }

    private static UnifiedUser userWithoutKaiten() {
        LocalDateTime now = LocalDateTime.now();
        return new UnifiedUser(1L, EMAIL, "boris", "Boris", null,
                null, null, now, now, now);
    }
}
