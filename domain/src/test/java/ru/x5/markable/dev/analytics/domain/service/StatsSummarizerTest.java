package ru.x5.markable.dev.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.stats.AuthorSummary;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.stats.PeriodSummary;
import ru.x5.markable.dev.analytics.domain.model.stats.WeeklyStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

@DisplayName("StatsSummarizer (pure-агрегация daily stats → period summary / weekly)")
class StatsSummarizerTest {

    private static final Period MAY = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    private static final RepoName REPO = new RepoName("xrg-core");

    @Test
    @DisplayName("summarize: суммирует totals, считает уникальных авторов, сортирует топ по убыванию коммитов")
    void summarizesTotalsAndTopAuthors() {
        List<DailyAuthorStats> stats = List.of(
                day(LocalDate.of(2026, 5, 1), "a@x5.ru", 3, 0, 10, 5, 2),
                day(LocalDate.of(2026, 5, 2), "a@x5.ru", 2, 1, 8, 4, 1),
                day(LocalDate.of(2026, 5, 1), "b@x5.ru", 1, 0, 5, 1, 0)
        );

        PeriodSummary s = StatsSummarizer.summarize(MAY, stats);

        assertAll("сводка за май",
                () -> assertThat(s.totalCommits()).as("3 + 2 + 1").isEqualTo(6),
                () -> assertThat(s.totalMergeCommits()).as("0 + 1 + 0").isEqualTo(1),
                () -> assertThat(s.totalAddedLines()).isEqualTo(23),
                () -> assertThat(s.totalDeletedLines()).isEqualTo(10),
                () -> assertThat(s.totalTestAddedLines()).isEqualTo(3),
                () -> assertThat(s.uniqueAuthors()).as("два уникальных email").isEqualTo(2),
                () -> assertThat(s.topAuthors())
                        .as("автор a первый — у него 5 коммитов, у b — 1")
                        .extracting(a -> a.email().value())
                        .containsExactly("a@x5.ru", "b@x5.ru"),
                () -> assertThat(s.topAuthors().getFirst().commits()).isEqualTo(5));
    }

    @Test
    @DisplayName("summarize: пустой вход → нули и пустой топ")
    void summarizeEmpty() {
        PeriodSummary s = StatsSummarizer.summarize(MAY, List.of());
        assertAll("сводка пустая",
                () -> assertThat(s.totalCommits()).isZero(),
                () -> assertThat(s.uniqueAuthors()).isZero(),
                () -> assertThat(s.topAuthors()).isEmpty());
    }

    @Test
    @DisplayName("weekly: группирует по ISO-неделям, сортирует по началу недели")
    void weeklyGroupsByIsoWeek() {
        // 2026-05-04 (Пн) — начало 19-й недели ISO 2026; 2026-05-11 (Пн) — 20-й
        List<DailyAuthorStats> stats = List.of(
                day(LocalDate.of(2026, 5, 4), "a@x5.ru", 1, 0, 1, 0, 0),
                day(LocalDate.of(2026, 5, 7), "a@x5.ru", 2, 0, 2, 0, 0),
                day(LocalDate.of(2026, 5, 11), "b@x5.ru", 5, 0, 10, 0, 0)
        );

        List<WeeklyStats> weeks = StatsSummarizer.weekly(stats);

        assertAll("две недели в порядке возрастания",
                () -> assertThat(weeks).hasSize(2),
                () -> assertThat(weeks.get(0).week()).as("первой идёт более ранняя неделя").isEqualTo(19),
                () -> assertThat(weeks.get(0).totalCommits()).isEqualTo(3),
                () -> assertThat(weeks.get(1).week()).isEqualTo(20),
                () -> assertThat(weeks.get(1).totalCommits()).isEqualTo(5),
                () -> assertThat(weeks.get(0).weekStart().getDayOfWeek().getValue())
                        .as("weekStart — понедельник").isEqualTo(1));
    }

    @Test
    @DisplayName("activeAuthorsByActivity: сортирует по убыванию не-мердж коммитов")
    void activeAuthorsSortedByNonMerge() {
        List<DailyAuthorStats> stats = List.of(
                // alpha: 10 коммитов, ВСЕ мерджи → реальная работа = 0
                day(LocalDate.of(2026, 5, 1), "alpha@x5.ru", 10, 10, 0, 0, 0),
                // beta: 8 коммитов, 0 мерджей → реальная работа = 8
                day(LocalDate.of(2026, 5, 2), "beta@x5.ru",  8, 0, 0, 0, 0),
                // gamma: 5 коммитов, 1 мердж → реальная работа = 4
                day(LocalDate.of(2026, 5, 3), "gamma@x5.ru", 5, 1, 0, 0, 0)
        );

        List<AuthorSummary> sorted = StatsSummarizer.activeAuthorsByActivity(stats);

        assertAll("сортировка по nonMergeCommits desc",
                () -> assertThat(sorted)
                        .extracting(a -> a.email().value())
                        .as("beta (8) > gamma (4) > alpha (0)")
                        .containsExactly("beta@x5.ru", "gamma@x5.ru", "alpha@x5.ru"),
                () -> assertThat(sorted.get(0).nonMergeCommits()).isEqualTo(8),
                () -> assertThat(sorted.get(1).nonMergeCommits()).isEqualTo(4),
                () -> assertThat(sorted.get(2).nonMergeCommits()).isZero(),
                () -> assertThat(sorted.get(2).commits()).as("у alpha commits всё равно 10").isEqualTo(10));
    }

    @Test
    @DisplayName("activeAuthorsByActivity: пустой вход → пустой список")
    void activeAuthorsEmpty() {
        assertThat(StatsSummarizer.activeAuthorsByActivity(List.of())).isEmpty();
    }

    @Test
    @DisplayName("activeAuthorsByActivity: ничья по non-merge → стабильный порядок (alphabet by email)")
    void activeAuthorsStableTieBreaker() {
        List<DailyAuthorStats> stats = List.of(
                day(LocalDate.of(2026, 5, 1), "zeta@x5.ru", 5, 0, 0, 0, 0),
                day(LocalDate.of(2026, 5, 1), "alpha@x5.ru", 5, 0, 0, 0, 0)
        );

        List<AuthorSummary> sorted = StatsSummarizer.activeAuthorsByActivity(stats);

        assertThat(sorted)
                .as("при равных коммитах сортируется по email алфавитно")
                .extracting(a -> a.email().value())
                .containsExactly("alpha@x5.ru", "zeta@x5.ru");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("emptyInputs")
    @DisplayName("weekly: пустой/null вход → пустой список")
    void weeklyEmpty(String label, List<DailyAuthorStats> input) {
        assertThat(StatsSummarizer.weekly(input)).isEmpty();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> emptyInputs() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("пустой список", List.of())
        );
    }

    private static DailyAuthorStats day(LocalDate date, String email,
                                        long commits, long merge,
                                        long added, long deleted, long testAdded) {
        return new DailyAuthorStats(
                null, new Email(email), date, REPO,
                commits, merge, added, deleted, testAdded,
                LocalDateTime.now(), null);
    }
}
