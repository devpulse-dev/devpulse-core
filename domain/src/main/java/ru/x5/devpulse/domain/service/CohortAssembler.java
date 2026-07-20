package ru.x5.devpulse.domain.service;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import ru.x5.devpulse.domain.model.cohort.CohortActivityMatrix;
import ru.x5.devpulse.domain.model.cohort.CohortRetention;
import ru.x5.devpulse.domain.model.cohort.DeveloperActivity;
import ru.x5.devpulse.domain.model.cohort.MonthlyAuthorActivity;
import ru.x5.devpulse.domain.model.cohort.TierTransitions;
import ru.x5.devpulse.domain.model.stats.ActivityCategory;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Чистая сборка когортных вью из помесячной активности.
 *
 * <p>Stateless, без I/O — как {@code StatsSummarizer}. Вход — список
 * {@link MonthlyAuthorActivity} (агрегаты {@code (email, month)} из БД). «Активен в месяце» =
 * {@code nonMergeCommits >= minCommits}. Ось месяцев — contiguous {@code [minMonth..maxMonth]}
 * из данных.</p>
 */
public final class CohortAssembler {

    /** Порядок тиров = ordinal {@link ActivityCategory}; на нём держится индексация матрицы переходов. */
    private static final List<ActivityCategory> TIERS = List.of(
            ActivityCategory.INACTIVE, ActivityCategory.BELOW_AVERAGE,
            ActivityCategory.ACTIVE, ActivityCategory.STAR);

    private CohortAssembler() {}

    /**
     * Retention-треугольник: когорта = месяц первой активности; {@code retention[k]} = доля
     * членов когорты, активных через k месяцев (retention[0] == 1).
     */
    public static CohortRetention retention(List<MonthlyAuthorActivity> activity, int minCommits) {
        List<YearMonth> axis = monthAxis(activity);
        if (axis.isEmpty()) return new CohortRetention(List.of());
        YearMonth lastMonth = axis.get(axis.size() - 1);

        // когорта → список множеств активных месяцев каждого члена
        Map<YearMonth, List<Set<YearMonth>>> cohortMembers = new TreeMap<>();
        for (var dev : byDeveloper(activity).entrySet()) {
            TreeSet<YearMonth> active = new TreeSet<>();
            for (MonthlyAuthorActivity a : dev.getValue().values()) {
                if (a.nonMergeCommits() >= minCommits) active.add(a.month());
            }
            if (active.isEmpty()) continue;
            cohortMembers.computeIfAbsent(active.first(), k -> new ArrayList<>()).add(active);
        }

        List<CohortRetention.CohortRow> rows = new ArrayList<>();
        for (var e : cohortMembers.entrySet()) { // TreeMap → когорты по возрастанию
            YearMonth cohort = e.getKey();
            List<Set<YearMonth>> members = e.getValue();
            int size = members.size();
            int maxK = (int) cohort.until(lastMonth, ChronoUnit.MONTHS);
            List<Double> retention = new ArrayList<>(maxK + 1);
            for (int k = 0; k <= maxK; k++) {
                YearMonth target = cohort.plusMonths(k);
                long activeAtK = members.stream().filter(m -> m.contains(target)).count();
                retention.add((double) activeAtK / size);
            }
            rows.add(new CohortRetention.CohortRow(cohort, size, retention));
        }
        return new CohortRetention(rows);
    }

    /**
     * Матрица «разработчик × месяц»: {@code cells} = не-мердж коммиты по месяцам (0 = неактивен).
     * Без enrichment'а — {@code displayName}/{@code avatarUrl}/{@code team} дозаполняет use case.
     * В матрицу попадают разработчики, активные (≥ minCommits) хотя бы в одном месяце.
     */
    public static CohortActivityMatrix activityMatrix(List<MonthlyAuthorActivity> activity, int minCommits) {
        List<YearMonth> axis = monthAxis(activity);
        if (axis.isEmpty()) return new CohortActivityMatrix(List.of(), List.of());

        List<DeveloperActivity> developers = new ArrayList<>();
        for (var dev : byDeveloper(activity).entrySet()) {
            TreeMap<YearMonth, MonthlyAuthorActivity> months = dev.getValue();
            List<Integer> cells = new ArrayList<>(axis.size());
            YearMonth firstActive = null;
            YearMonth lastActive = null;
            for (YearMonth m : axis) {
                MonthlyAuthorActivity a = months.get(m);
                int nm = a == null ? 0 : (int) a.nonMergeCommits();
                cells.add(nm);
                if (nm >= minCommits) {
                    if (firstActive == null) firstActive = m;
                    lastActive = m;
                }
            }
            if (firstActive == null) continue; // ни одного активного месяца по порогу
            developers.add(new DeveloperActivity(dev.getKey(), null, null, null,
                    firstActive, lastActive, cells));
        }
        developers.sort(Comparator.comparing(DeveloperActivity::firstActive)
                .thenComparing(d -> d.email().value()));
        return new CohortActivityMatrix(axis, developers);
    }

    /**
     * 4×4 матрица переходов тиров месяц-к-месяцу. Для каждого разработчика от его первого
     * активного месяца до конца оси считаем категорию каждого месяца (нет активности → INACTIVE,
     * иначе {@link ActivityScorer}), и копим переходы между соседними месяцами. Строки нормированы.
     */
    public static TierTransitions tierTransitions(List<MonthlyAuthorActivity> activity,
                                                  int minCommits,
                                                  double expectedCommitsPer30Days) {
        int[][] counts = new int[TIERS.size()][TIERS.size()];
        List<YearMonth> axis = monthAxis(activity);
        if (!axis.isEmpty()) {
            YearMonth lastMonth = axis.get(axis.size() - 1);
            for (var dev : byDeveloper(activity).entrySet()) {
                TreeMap<YearMonth, MonthlyAuthorActivity> months = dev.getValue();
                YearMonth firstActive = firstActiveMonth(months, minCommits);
                if (firstActive == null) continue;

                ActivityCategory prev = null;
                for (YearMonth m = firstActive; !m.isAfter(lastMonth); m = m.plusMonths(1)) {
                    ActivityCategory cat = categoryFor(months.get(m), expectedCommitsPer30Days);
                    if (prev != null) counts[prev.ordinal()][cat.ordinal()]++;
                    prev = cat;
                }
            }
        }
        return normalize(counts);
    }

    /* ----------------------------- helpers ----------------------------- */

    private static Map<Email, TreeMap<YearMonth, MonthlyAuthorActivity>> byDeveloper(
            List<MonthlyAuthorActivity> activity) {
        Map<Email, TreeMap<YearMonth, MonthlyAuthorActivity>> map = new LinkedHashMap<>();
        for (MonthlyAuthorActivity a : activity) {
            map.computeIfAbsent(a.email(), k -> new TreeMap<>()).put(a.month(), a);
        }
        return map;
    }

    /** Contiguous ось месяцев [min..max] из данных. */
    private static List<YearMonth> monthAxis(List<MonthlyAuthorActivity> activity) {
        YearMonth min = null;
        YearMonth max = null;
        for (MonthlyAuthorActivity a : activity) {
            if (min == null || a.month().isBefore(min)) min = a.month();
            if (max == null || a.month().isAfter(max)) max = a.month();
        }
        if (min == null) return List.of();
        List<YearMonth> axis = new ArrayList<>();
        for (YearMonth m = min; !m.isAfter(max); m = m.plusMonths(1)) axis.add(m);
        return axis;
    }

    private static YearMonth firstActiveMonth(TreeMap<YearMonth, MonthlyAuthorActivity> months, int minCommits) {
        for (MonthlyAuthorActivity a : months.values()) { // TreeMap → по возрастанию месяца
            if (a.nonMergeCommits() >= minCommits) return a.month();
        }
        return null;
    }

    private static ActivityCategory categoryFor(MonthlyAuthorActivity a, double expectedPer30) {
        if (a == null || a.nonMergeCommits() == 0) return ActivityCategory.INACTIVE;
        double expected = ActivityScorer.scaleExpectedForDays(expectedPer30, a.month().lengthOfMonth());
        AuthorSummary summary = new AuthorSummary(a.email(), null, null,
                a.commits(), a.mergeCommits(), a.addedLines(), a.deletedLines(), 0L, null, null, false);
        return ActivityScorer.score(summary, expected).category();
    }

    private static TierTransitions normalize(int[][] counts) {
        List<List<Double>> matrix = new ArrayList<>(TIERS.size());
        for (int i = 0; i < TIERS.size(); i++) {
            int rowSum = 0;
            for (int j = 0; j < TIERS.size(); j++) rowSum += counts[i][j];
            List<Double> row = new ArrayList<>(TIERS.size());
            for (int j = 0; j < TIERS.size(); j++) {
                row.add(rowSum == 0 ? 0.0 : (double) counts[i][j] / rowSum);
            }
            matrix.add(row);
        }
        return new TierTransitions(TIERS, matrix);
    }
}
