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
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.stats.Dashboard;
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
    @DisplayName("dashboard: топ-2 и аутсайдеры-2 при 4 авторах с разной активностью")
    void dashboardSplitsTopAndOutsiders() {
        List<DailyAuthorStats> stats = List.of(
                day(LocalDate.of(2026, 5, 1), "alpha@x5.ru", 10, 0, 0, 0, 0),
                day(LocalDate.of(2026, 5, 2), "beta@x5.ru",  8, 0, 0, 0, 0),
                day(LocalDate.of(2026, 5, 3), "gamma@x5.ru", 3, 0, 0, 0, 0),
                day(LocalDate.of(2026, 5, 4), "delta@x5.ru", 1, 0, 0, 0, 0)
        );

        Dashboard d = StatsSummarizer.dashboard(MAY, stats, 2, 2);

        assertAll("dashboard",
                () -> assertThat(d.period()).isEqualTo(MAY),
                () -> assertThat(d.topActive())
                        .extracting(a -> a.email().value())
                        .containsExactly("alpha@x5.ru", "beta@x5.ru"),
                () -> assertThat(d.outsiders())
                        .extracting(a -> a.email().value())
                        .as("снизу вверх — delta меньше всех, gamma следом")
                        .containsExactly("delta@x5.ru", "gamma@x5.ru"));
    }

    @Test
    @DisplayName("dashboard: пустой вход → пустые списки, период сохраняется")
    void dashboardEmpty() {
        Dashboard d = StatsSummarizer.dashboard(MAY, List.of(), 10, 10);
        assertAll("пустой dashboard",
                () -> assertThat(d.period()).isEqualTo(MAY),
                () -> assertThat(d.topActive()).isEmpty(),
                () -> assertThat(d.outsiders()).isEmpty());
    }

    @Test
    @DisplayName("dashboard: маленькая команда (2 автора, topN=3, outsiderN=3) → списки пересекаются, оба <=2")
    void dashboardSmallTeamOverlapsAllowed() {
        List<DailyAuthorStats> stats = List.of(
                day(LocalDate.of(2026, 5, 1), "a@x5.ru", 5, 0, 0, 0, 0),
                day(LocalDate.of(2026, 5, 2), "b@x5.ru", 2, 0, 0, 0, 0)
        );

        Dashboard d = StatsSummarizer.dashboard(MAY, stats, 3, 3);

        assertAll("маленькая команда",
                () -> assertThat(d.topActive()).hasSize(2),
                () -> assertThat(d.outsiders()).hasSize(2),
                () -> assertThat(d.topActive().getFirst().email().value()).isEqualTo("a@x5.ru"),
                () -> assertThat(d.outsiders().getFirst().email().value()).isEqualTo("b@x5.ru"));
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
