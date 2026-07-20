package ru.x5.devpulse.domain.service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.stats.PeriodSummary;
import ru.x5.devpulse.domain.model.stats.WeeklyAuthorActivity;
import ru.x5.devpulse.domain.model.stats.WeeklyStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Pure-агрегация набора {@link DailyAuthorStats} в query-DTO домена:
 * {@link PeriodSummary} (один сводный объект) и {@link WeeklyStats} (список по ISO-неделям).
 *
 * <p>Никаких I/O, никаких побочных эффектов — детерминированная функция.</p>
 */
public final class StatsSummarizer {

    private static final int TOP_AUTHORS = 10;

    private StatsSummarizer() {}

    /**
     * Сводка за период из <b>уже агрегированных по автору</b> строк (см.
     * {@code DailyStatsRepository.aggregateAuthorsByPeriod}, где свёртка daily→автор сделана в БД):
     * totals как сумма по авторам + top-{@value #TOP_AUTHORS} по убыванию коммитов. На вход — по
     * строке на автора (не сырые daily-строки), heap не растёт с длиной периода.
     */
    public static PeriodSummary summarizeAuthors(Period period, Collection<AuthorSummary> authors) {
        long totalCommits = 0;
        long totalMerge = 0;
        long totalAdded = 0;
        long totalDeleted = 0;
        long totalTestAdded = 0;
        for (AuthorSummary a : authors) {
            totalCommits += a.commits();
            totalMerge += a.mergeCommits();
            totalAdded += a.addedLines();
            totalDeleted += a.deletedLines();
            totalTestAdded += a.testAddedLines();
        }
        List<AuthorSummary> top = authors.stream()
                .sorted(Comparator.comparingLong(AuthorSummary::commits).reversed())
                .limit(TOP_AUTHORS)
                .toList();
        return new PeriodSummary(
                period,
                totalCommits, totalMerge, totalAdded, totalDeleted, totalTestAdded,
                authors.size(),
                top);
    }

    /**
     * Группирует daily-агрегаты по ISO-неделям, для каждой недели даёт totals + per-author breakdown.
     * Список отсортирован по началу недели возрастающе.
     *
     * <p><b>Осиротел:</b> прод перешёл на {@link #weeklyFromAggregates} (свёртка (email, неделя) в
     * БД, без подъёма всех daily-строк в heap). Метод пока сохранён под регрессионные тесты
     * weekStart-детерминизма; удалить при следующей чистке мёртвого кода.</p>
     */
    public static List<WeeklyStats> weekly(Collection<DailyAuthorStats> stats) {
        Map<WeekKey, WeekAcc> byWeek = new HashMap<>();

        for (DailyAuthorStats s : stats) {
            LocalDate date = s.date();
            int isoWeek = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            int isoYear = date.get(WeekFields.ISO.weekBasedYear());
            WeekKey key = new WeekKey(isoYear, isoWeek);
            byWeek.computeIfAbsent(key, k -> new WeekAcc()).add(s);
        }

        List<WeeklyStats> result = new ArrayList<>(byWeek.size());
        for (Map.Entry<WeekKey, WeekAcc> e : byWeek.entrySet()) {
            result.add(e.getValue().toWeeklyStats(e.getKey()));
        }
        result.sort(Comparator.comparing(WeeklyStats::weekStart));
        return result;
    }

    /**
     * Reshape SQL-агрегата {@code (email, ISO-неделя)} в недельную статистику с per-author breakdown.
     * Группирует по {@code (isoYear, isoWeek)}, для каждой недели — totals + отсортированный список
     * авторов. Свёртка daily→(email, неделя) сделана в БД (см. {@code weeklyAuthorActivity}), heap не
     * растёт с длиной периода — в отличие от {@link #weekly(Collection)}.
     */
    public static List<WeeklyStats> weeklyFromAggregates(Collection<WeeklyAuthorActivity> rows) {
        Map<WeekKey, WeekAgg> byWeek = new HashMap<>();
        for (WeeklyAuthorActivity r : rows) {
            byWeek.computeIfAbsent(new WeekKey(r.isoYear(), r.isoWeek()), k -> new WeekAgg()).add(r);
        }
        List<WeeklyStats> result = new ArrayList<>(byWeek.size());
        for (Map.Entry<WeekKey, WeekAgg> e : byWeek.entrySet()) {
            result.add(e.getValue().toWeeklyStats(e.getKey()));
        }
        result.sort(Comparator.comparing(WeeklyStats::weekStart));
        return result;
    }

    /* ---------------- internals ---------------- */

    private record WeekKey(int year, int week) {}

    /**
     * Понедельник ISO-недели {@code week} года {@code year}.
     *
     * <p>Детерминированно (без {@code LocalDate.now()}): 4 января — <b>всегда</b> в ISO week 1
     * любого года (по определению ISO 8601). От него идём к понедельнику этой недели
     * (день 1 в ISO), потом сдвигаемся на {@code week - 1} неделю.</p>
     *
     * <p>Старая реализация {@code LocalDate.now().withYear(year)...} могла отъехать на день,
     * если "сегодня" — 29 февраля високосного года, а целевой {@code year} — не-leap
     * ({@code withYear} в такой ситуации откатывает до 28 февраля).</p>
     */
    private static LocalDate weekStart(int year, int week) {
        LocalDate firstWeekMonday = LocalDate.of(year, 1, 4)
                .with(WeekFields.ISO.dayOfWeek(), 1);
        return firstWeekMonday.plusWeeks(week - 1L);
    }

    private static final class AuthorAcc {
        long commits;
        long mergeCommits;
        long addedLines;
        long deletedLines;
        long testAddedLines;

        void add(DailyAuthorStats s) {
            commits += s.commits();
            mergeCommits += s.mergeCommits();
            addedLines += s.addedLines();
            deletedLines += s.deletedLines();
            testAddedLines += s.testAddedLines();
        }

        AuthorSummary toSummary(Email email) {
            // displayName, avatarUrl и activity — null. Enrichment делает use case из
            // unified_user; activity считается только в GetDashboardService.
            return new AuthorSummary(email, null, null,
                    commits, mergeCommits, addedLines, deletedLines, testAddedLines, null, null, false);
        }
    }

    private static final class WeekAcc {
        long commits;
        long mergeCommits;
        long addedLines;
        long deletedLines;
        long testAddedLines;
        Map<Email, AuthorAcc> authors = new HashMap<>();

        void add(DailyAuthorStats s) {
            commits += s.commits();
            mergeCommits += s.mergeCommits();
            addedLines += s.addedLines();
            deletedLines += s.deletedLines();
            testAddedLines += s.testAddedLines();
            authors.computeIfAbsent(s.authorEmail(), k -> new AuthorAcc()).add(s);
        }

        WeeklyStats toWeeklyStats(WeekKey key) {
            List<AuthorSummary> authorList = authors.entrySet().stream()
                    .map(e -> e.getValue().toSummary(e.getKey()))
                    .sorted(Comparator.comparingLong(AuthorSummary::commits).reversed())
                    .toList();
            return new WeeklyStats(
                    key.year, key.week, weekStart(key.year, key.week),
                    commits, mergeCommits, addedLines, deletedLines, testAddedLines,
                    authorList
            );
        }
    }

    /** Аккумулятор недели из уже per-author SQL-агрегата (для {@link #weeklyFromAggregates}). */
    private static final class WeekAgg {
        long commits;
        long mergeCommits;
        long added;
        long deleted;
        long testAdded;
        final List<AuthorSummary> authors = new ArrayList<>();

        void add(WeeklyAuthorActivity r) {
            commits += r.commits();
            mergeCommits += r.mergeCommits();
            added += r.addedLines();
            deleted += r.deletedLines();
            testAdded += r.testAddedLines();
            authors.add(new AuthorSummary(r.email(), null, null,
                    r.commits(), r.mergeCommits(), r.addedLines(), r.deletedLines(), r.testAddedLines(),
                    null, null, false));
        }

        WeeklyStats toWeeklyStats(WeekKey key) {
            List<AuthorSummary> sorted = authors.stream()
                    .sorted(Comparator.comparingLong(AuthorSummary::commits).reversed())
                    .toList();
            return new WeeklyStats(key.year, key.week, weekStart(key.year, key.week),
                    commits, mergeCommits, added, deleted, testAdded, sorted);
        }
    }
}
