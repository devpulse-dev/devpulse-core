package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import ru.x5.devpulse.application.port.in.GetUserProfileUseCase;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("Query use case-ы (read-side)")
class QueryUseCasesTest {

    private static final Period PERIOD = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    private static final Email EMAIL = new Email("boris@x5.ru");
    private static final RepoName REPO = new RepoName("xrg-core");

    @Mock DailyStatsRepository dailyStatsRepository;
    @Mock CommitRepository commitRepository;
    @Mock UnifiedUserRepository unifiedUserRepository;
    @Mock KaitenGateway kaitenGateway;

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
        @DisplayName("группирует по ISO-неделям + enrich displayName/avatarUrl из unified_user")
        void groupsByWeekAndEnriches() {
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(List.of(
                    day(EMAIL, LocalDate.of(2026, 5, 4), 1, 0),    // ISO week 19
                    day(EMAIL, LocalDate.of(2026, 5, 11), 2, 0)    // ISO week 20
            ));
            when(unifiedUserRepository.findByEmails(anyCollection())).thenReturn(List.of(
                    userWithProfile(EMAIL, "Boris", "https://avatar/b")
            ));

            var weeks = new GetWeeklyStatsService(dailyStatsRepository, unifiedUserRepository)
                    .findByPeriod(PERIOD);

            assertAll("weekly enriched",
                    () -> assertThat(weeks).hasSize(2),
                    () -> assertThat(weeks.get(0).authors().getFirst().displayName()).isEqualTo("Boris"),
                    () -> assertThat(weeks.get(0).authors().getFirst().avatarUrl()).isEqualTo("https://avatar/b"));
        }

        @Test
        @DisplayName("пустые stats → пустой список, без обращения к unified_user")
        void emptyWeeks() {
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(List.of());

            var weeks = new GetWeeklyStatsService(dailyStatsRepository, unifiedUserRepository)
                    .findByPeriod(PERIOD);

            assertAll("пустой weekly",
                    () -> assertThat(weeks).isEmpty(),
                    () -> verifyNoInteractions(unifiedUserRepository));
        }
    }

    @Nested
    @DisplayName("GetPeriodSummaryService")
    class Summary {
        @Test
        @DisplayName("период с пустым набором → нулевая сводка, unified_user не зовём")
        void emptyPeriod() {
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(List.of());

            var s = new GetPeriodSummaryService(dailyStatsRepository, unifiedUserRepository)
                    .summarize(PERIOD);

            assertAll("пустая сводка",
                    () -> assertThat(s.period()).isEqualTo(PERIOD),
                    () -> assertThat(s.totalCommits()).isZero(),
                    () -> assertThat(s.topAuthors()).isEmpty(),
                    () -> verifyNoInteractions(unifiedUserRepository));
        }

        @Test
        @DisplayName("topAuthors enriched displayName/avatarUrl из unified_user")
        void summaryEnrichesTopAuthors() {
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(List.of(
                    day(EMAIL, LocalDate.of(2026, 5, 1), 5, 0)
            ));
            when(unifiedUserRepository.findByEmails(anyCollection())).thenReturn(List.of(
                    userWithProfile(EMAIL, "Boris", "https://avatar/b")
            ));

            var s = new GetPeriodSummaryService(dailyStatsRepository, unifiedUserRepository)
                    .summarize(PERIOD);

            assertAll("topAuthors enriched",
                    () -> assertThat(s.topAuthors()).hasSize(1),
                    () -> assertThat(s.topAuthors().getFirst().displayName()).isEqualTo("Boris"),
                    () -> assertThat(s.topAuthors().getFirst().avatarUrl()).isEqualTo("https://avatar/b"));
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
                    () -> verifyNoInteractions(kaitenGateway));
        }

        @Test
        @DisplayName("Есть kaiten_id → дёргаем kaiten cards live из gateway, summary из daily stats")
        void profileWithKaiten() {
            UnifiedUser user = userWithKaiten(7L);
            when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(dailyStatsRepository.findByAuthorAndPeriod(EMAIL, PERIOD)).thenReturn(List.of(
                    day(LocalDate.of(2026, 5, 1), 3),
                    day(LocalDate.of(2026, 5, 2), 2)
            ));
            when(commitRepository.findByAuthor(eq(EMAIL), eq(PERIOD), any())).thenReturn(List.of());
            when(kaitenGateway.fetchCardsForMember(eq(new KaitenUserId(7L)), any())).thenReturn(List.of());

            GetUserProfileUseCase.Profile p = service().findProfile(EMAIL, PERIOD).orElseThrow();

            assertAll("профиль с kaiten",
                    () -> assertThat(p.user()).isSameAs(user),
                    () -> assertThat(p.summary().commits()).as("3 + 2").isEqualTo(5),
                    () -> assertThat(p.summary().email()).isEqualTo(EMAIL),
                    () -> verify(kaitenGateway).fetchCardsForMember(eq(new KaitenUserId(7L)), any()));
        }

        @Test
        @DisplayName("Фильтр карточек: закрытая БЕЗ коммитов автора отсеивается, остальные остаются")
        void filtersClosedCardsWithoutCommits() {
            UnifiedUser user = userWithKaiten(7L);
            when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(dailyStatsRepository.findByAuthorAndPeriod(EMAIL, PERIOD)).thenReturn(List.of());

            // Коммит автора по карточке #200 (taskNumber → kaitenCardId)
            when(commitRepository.findByAuthor(eq(EMAIL), eq(PERIOD), any())).thenReturn(List.of(
                    commitForCard("200")
            ));

            // 4 карточки Kaiten:
            //  100 — OPEN (in progress), без коммитов автора → ПОКАЗЫВАЕМ (висит у пользователя)
            //  200 — DONE, но с коммитом автора                → ПОКАЗЫВАЕМ (работал по ней)
            //  300 — DONE, без коммитов автора                 → ОТСЕИВАЕМ
            //  400 — NEW, без коммитов                         → ПОКАЗЫВАЕМ
            when(kaitenGateway.fetchCardsForMember(eq(new KaitenUserId(7L)), any())).thenReturn(List.of(
                    card(100L, /*colType*/ 2 /*IN_PROGRESS*/),
                    card(200L, /*colType*/ 3 /*DONE*/),
                    card(300L, /*colType*/ 3 /*DONE*/),
                    card(400L, /*colType*/ 1 /*NEW*/)
            ));

            var p = service().findProfile(EMAIL, PERIOD).orElseThrow();

            assertThat(p.cards())
                    .as("закрытая без коммитов (300) отсеяна; остальные показываются")
                    .extracting(c -> c.id().value())
                    .containsExactlyInAnyOrder(100L, 200L, 400L);
        }

        @Test
        @DisplayName("Нет kaiten_id → cards пустые, KaitenGateway НЕ зовём")
        void profileWithoutKaiten() {
            UnifiedUser user = userWithoutKaiten();
            when(unifiedUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(dailyStatsRepository.findByAuthorAndPeriod(EMAIL, PERIOD)).thenReturn(List.of());
            when(commitRepository.findByAuthor(eq(EMAIL), eq(PERIOD), any())).thenReturn(List.of());

            var p = service().findProfile(EMAIL, PERIOD).orElseThrow();

            assertAll("профиль без kaiten",
                    () -> assertThat(p.cards()).isEmpty(),
                    () -> verify(kaitenGateway, never()).fetchCardsForMember(any(), any()));
        }

        private GetUserProfileService service() {
            return new GetUserProfileService(
                    unifiedUserRepository, dailyStatsRepository, commitRepository, kaitenGateway);
        }
    }

    @Nested
    @DisplayName("GetDashboardService")
    class DashboardService {
        @Test
        @DisplayName("paginated + enrich avatar/name из unified_user")
        void paginatedAndEnriched() {
            // 3 автора с разной активностью
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(List.of(
                    day(EMAIL, LocalDate.of(2026, 5, 1), 10, 0),
                    day(new Email("alice@x5.ru"), LocalDate.of(2026, 5, 2), 5, 0),
                    day(new Email("bob@x5.ru"), LocalDate.of(2026, 5, 3), 1, 0)
            ));

            // unified_user знает только Boris-а — alice/bob ещё не синканы
            when(unifiedUserRepository.findByEmails(anyCollection())).thenReturn(List.of(
                    userWithProfile(EMAIL, "Boris", "https://avatar/boris")
            ));

            var d = new GetDashboardService(dailyStatsRepository, unifiedUserRepository, 50.0)
                    .get(PERIOD, new PageRequest(0, 2));

            assertAll("paginated dashboard",
                    () -> assertThat(d.period()).isEqualTo(PERIOD),
                    () -> assertThat(d.authors().items()).hasSize(2),
                    () -> assertThat(d.authors().totalElements()).as("всего 3 активных").isEqualTo(3),
                    () -> assertThat(d.authors().totalPages()).isEqualTo(2),
                    () -> assertThat(d.authors().hasNext()).isTrue(),
                    () -> assertThat(d.authors().items().get(0).email()).as("Boris в топе — 10 коммитов").isEqualTo(EMAIL),
                    () -> assertThat(d.authors().items().get(0).displayName()).isEqualTo("Boris"),
                    () -> assertThat(d.authors().items().get(0).avatarUrl()).isEqualTo("https://avatar/boris"),
                    () -> assertThat(d.authors().items().get(1).email().value()).isEqualTo("alice@x5.ru"),
                    () -> assertThat(d.authors().items().get(1).displayName()).as("alice не в unified_user → null").isNull(),
                    // У каждого автора должен быть рассчитан activity score.
                    () -> assertThat(d.authors().items().get(0).activity())
                            .as("Boris — activity заполнен").isNotNull(),
                    () -> assertThat(d.authors().items().get(0).activity().score())
                            .as("Boris (10 коммитов в месячном периоде на baseline 50) — score > 0")
                            .isPositive(),
                    () -> assertThat(d.authors().items().get(1).activity())
                            .as("Alice тоже scored, даже без enrichment").isNotNull());
        }

        @Test
        @DisplayName("пустые stats → пустая страница с правильным page/size, без обращения к unified_user")
        void emptyStats() {
            when(dailyStatsRepository.findByPeriod(PERIOD)).thenReturn(List.of());

            var d = new GetDashboardService(dailyStatsRepository, unifiedUserRepository, 50.0)
                    .get(PERIOD, new PageRequest(0, 10));

            assertAll("пустая страница",
                    () -> assertThat(d.authors().items()).isEmpty(),
                    () -> assertThat(d.authors().totalElements()).isZero(),
                    () -> assertThat(d.authors().page()).isZero(),
                    () -> assertThat(d.authors().size()).isEqualTo(10),
                    () -> org.mockito.Mockito.verifyNoInteractions(unifiedUserRepository));
        }
    }

    /* ------------ helpers ------------ */

    private static DailyAuthorStats day(LocalDate date, long commits) {
        return day(EMAIL, date, commits, /*merge*/ 0);
    }

    private static DailyAuthorStats day(Email email, LocalDate date, long commits, long merge) {
        return new DailyAuthorStats(
                null, email, date, REPO, commits, merge, 10, 5, 1,
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

    private static UnifiedUser userWithProfile(Email email, String name, String avatarUrl) {
        LocalDateTime now = LocalDateTime.now();
        return new UnifiedUser(1L, email, email.value().split("@")[0], name, avatarUrl,
                null, null, now, now, now);
    }

    private static ru.x5.devpulse.domain.model.git.Commit commitForCard(String cardIdString) {
        return new ru.x5.devpulse.domain.model.git.Commit(
                new ru.x5.devpulse.domain.model.git.CommitHash("a".repeat(40)),
                EMAIL,
                LocalDateTime.of(2026, 5, 15, 12, 0),
                /*merge*/ false,
                10, 5, 0,
                "fix",
                new ru.x5.devpulse.domain.common.TaskNumber(cardIdString),
                REPO);
    }

    private static ru.x5.devpulse.domain.model.kaiten.KaitenCard card(long id, int columnType) {
        LocalDateTime now = LocalDateTime.now();
        return new ru.x5.devpulse.domain.model.kaiten.KaitenCard(
                new ru.x5.devpulse.domain.model.kaiten.KaitenCardId(id),
                "card " + id, null,
                /*typeId*/ 70, /*columnType*/ columnType, /*columnTitle*/ "col-" + columnType,
                "Board", "Space",
                null, null, now, now, null, false,
                "https://kaiten.x5.ru/" + id,
                List.of());
    }
}
