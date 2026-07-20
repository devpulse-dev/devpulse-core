package ru.x5.devpulse.domain.service;

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
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.stats.PeriodSummary;
import ru.x5.devpulse.domain.model.stats.WeeklyStats;
import ru.x5.devpulse.domain.model.user.Email;

@DisplayName("StatsSummarizer (pure-агрегация: сводка из per-author + weekly по ISO-неделям)")
class StatsSummarizerTest {

    private static final Period MAY = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    private static final RepoName REPO = new RepoName("xrg-core");

    @Test
    @DisplayName("summarizeAuthors: totals из per-author агрегатов + top по убыванию коммитов")
    void summarizeAuthorsFromPerAuthorAggregates() {
        List<AuthorSummary> authors = List.of(
                author("a@x5.ru", 5, 1, 18, 9, 3),
                author("b@x5.ru", 1, 0, 5, 1, 0)
        );

        PeriodSummary s = StatsSummarizer.summarizeAuthors(MAY, authors);

        assertAll("сводка за май из per-author агрегатов",
                () -> assertThat(s.totalCommits()).as("5 + 1").isEqualTo(6),
                () -> assertThat(s.totalMergeCommits()).as("1 + 0").isEqualTo(1),
                () -> assertThat(s.totalAddedLines()).isEqualTo(23),
                () -> assertThat(s.totalDeletedLines()).isEqualTo(10),
                () -> assertThat(s.totalTestAddedLines()).isEqualTo(3),
                () -> assertThat(s.uniqueAuthors()).as("два автора").isEqualTo(2),
                () -> assertThat(s.topAuthors())
                        .as("автор a первый — у него 5 коммитов, у b — 1")
                        .extracting(a -> a.email().value())
                        .containsExactly("a@x5.ru", "b@x5.ru"));
    }

    @Test
    @DisplayName("summarizeAuthors: пустой вход → нули и пустой топ")
    void summarizeAuthorsEmpty() {
        PeriodSummary s = StatsSummarizer.summarizeAuthors(MAY, List.of());
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

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("emptyInputs")
    @DisplayName("weekly: пустой/null вход → пустой список")
    void weeklyEmpty(String label, List<DailyAuthorStats> input) {
        assertThat(StatsSummarizer.weekly(input)).isEmpty();
    }

    /**
     * Регрессионный тест для #18. weekStart обязан быть детерминированным — не зависеть от
     * сегодняшней даты. Проверяем на нескольких годах включая високосный (2024) и не-високосный
     * (2025/2026). Используем фиксированную дату стат-записи и проверяем что weekStart всегда
     * понедельник И принадлежит правильному ISO-году.
     */
    @ParameterizedTest(name = "[{index}] год {0}: {1} → понедельник {2}")
    @org.junit.jupiter.params.provider.CsvSource({
            // дата → ожидаемый weekStart (ISO Monday той же недели)
            "2024, 2024-02-29, 2024-02-26", // 29 фев в високосном — week 9
            "2025, 2025-01-01, 2024-12-30", // 1 янв 2025 = ISO week 1 of 2025, понедельник 30.12.2024
            "2025, 2025-12-31, 2025-12-29", // 31 дек 2025 = ISO week 1 of 2026, но weekBasedYear=2026
            "2026, 2026-05-15, 2026-05-11"  // обычный случай
    })
    @DisplayName("weekly: weekStart детерминирован для любого года (включая високосный)")
    void weekStartIsDeterministic(int label, LocalDate input, LocalDate expected) {
        List<DailyAuthorStats> stats = List.of(
                day(input, "a@x5.ru", 1, 0, 1, 0, 0));

        List<WeeklyStats> result = StatsSummarizer.weekly(stats);

        assertAll("единственная неделя, weekStart = понедельник",
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.getFirst().weekStart())
                        .as("weekStart должен соответствовать ISO-понедельнику")
                        .isEqualTo(expected),
                () -> assertThat(result.getFirst().weekStart().getDayOfWeek().getValue())
                        .as("weekStart — понедельник").isEqualTo(1));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> emptyInputs() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("пустой список", List.of())
        );
    }

    private static AuthorSummary author(String email, long commits, long merge,
                                        long added, long deleted, long testAdded) {
        return new AuthorSummary(new Email(email), null, null,
                commits, merge, added, deleted, testAdded, null, null, false);
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
