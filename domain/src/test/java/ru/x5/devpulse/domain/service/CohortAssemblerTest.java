package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.model.cohort.CohortActivityMatrix;
import ru.x5.devpulse.domain.model.cohort.CohortRetention;
import ru.x5.devpulse.domain.model.cohort.MonthlyAuthorActivity;
import ru.x5.devpulse.domain.model.cohort.TierTransitions;
import ru.x5.devpulse.domain.model.stats.ActivityCategory;
import ru.x5.devpulse.domain.model.user.Email;

@DisplayName("CohortAssembler")
class CohortAssemblerTest {

    private static MonthlyAuthorActivity m(String email, String ym, long commits, long added) {
        return new MonthlyAuthorActivity(new Email(email), YearMonth.parse(ym), commits, 0, added, 0);
    }

    /** A: Jan,Feb,Mar; B: Jan,Mar (провал в Feb); C: Feb. minCommits=1. */
    private static final List<MonthlyAuthorActivity> SAMPLE = List.of(
            m("a@x5.ru", "2026-01", 5, 100), m("a@x5.ru", "2026-02", 5, 100), m("a@x5.ru", "2026-03", 5, 100),
            m("b@x5.ru", "2026-01", 5, 100), m("b@x5.ru", "2026-03", 5, 100),
            m("c@x5.ru", "2026-02", 5, 100));

    @Nested
    @DisplayName("retention")
    class Retention {

        @Test
        @DisplayName("Когорта = месяц первой активности; retention[k] = доля активных через k мес")
        void triangle() {
            CohortRetention r = CohortAssembler.retention(SAMPLE, 1);

            assertAll("треугольник",
                    () -> assertThat(r.cohorts()).extracting(c -> c.cohort().toString())
                            .containsExactly("2026-01", "2026-02"),
                    () -> assertThat(r.cohorts().get(0).size()).as("когорта Jan = {A,B}").isEqualTo(2),
                    () -> assertThat(r.cohorts().get(0).retention())
                            .as("Jan: k0 оба, k1 только A, k2 оба").containsExactly(1.0, 0.5, 1.0),
                    () -> assertThat(r.cohorts().get(1).size()).as("когорта Feb = {C}").isEqualTo(1),
                    () -> assertThat(r.cohorts().get(1).retention())
                            .as("Feb: k0 активен, k1 (Mar) уже нет").containsExactly(1.0, 0.0));
        }

        @Test
        @DisplayName("Пустой вход → пустой треугольник")
        void empty() {
            assertThat(CohortAssembler.retention(List.of(), 1).cohorts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("activityMatrix")
    class Matrix {

        @Test
        @DisplayName("Ось месяцев contiguous; cells = не-мердж по месяцам; сортировка по firstActive,email")
        void matrix() {
            CohortActivityMatrix m = CohortAssembler.activityMatrix(SAMPLE, 1);

            assertAll("матрица",
                    () -> assertThat(m.months()).extracting(YearMonth::toString)
                            .containsExactly("2026-01", "2026-02", "2026-03"),
                    () -> assertThat(m.developers()).extracting(d -> d.email().value())
                            .as("порядок: A,B (firstActive Jan), затем C (Feb)")
                            .containsExactly("a@x5.ru", "b@x5.ru", "c@x5.ru"),
                    () -> assertThat(m.developers().get(0).cells()).as("A").containsExactly(5, 5, 5),
                    () -> assertThat(m.developers().get(1).cells()).as("B — провал в Feb").containsExactly(5, 0, 5),
                    () -> assertThat(m.developers().get(2).cells()).as("C").containsExactly(0, 5, 0),
                    () -> assertThat(m.developers().get(1).firstActive().toString()).isEqualTo("2026-01"),
                    () -> assertThat(m.developers().get(1).lastActive().toString()).isEqualTo("2026-03"),
                    () -> assertThat(m.developers().get(2).firstActive().toString()).isEqualTo("2026-02"));
        }
    }

    @Nested
    @DisplayName("tierTransitions")
    class Transitions {

        @Test
        @DisplayName("Churn: STAR в Jan → INACTIVE далее; STAR→INACTIVE и INACTIVE→INACTIVE = 1.0")
        void churn() {
            // expected=10. Jan: 30 не-мердж, avg 100 строк/коммит → quality 1.0, volume 3 → score 3 → STAR.
            // Feb/Mar нет активности → INACTIVE. Ось Jan..Mar по данным одного дева.
            List<MonthlyAuthorActivity> one = List.of(
                    m("d@x5.ru", "2026-01", 30, 3000), // STAR
                    m("d@x5.ru", "2026-03", 1, 5));    // 1 коммит → score < 0.2 → INACTIVE; Feb — пропуск → INACTIVE

            TierTransitions t = CohortAssembler.tierTransitions(one, 1, 10.0);

            int inactive = ActivityCategory.INACTIVE.ordinal();
            int star = ActivityCategory.STAR.ordinal();
            assertAll("переходы",
                    () -> assertThat(t.tiers()).containsExactly(
                            ActivityCategory.INACTIVE, ActivityCategory.BELOW_AVERAGE,
                            ActivityCategory.ACTIVE, ActivityCategory.STAR),
                    () -> assertThat(t.matrix().get(star).get(inactive))
                            .as("единственный переход из STAR → в INACTIVE").isEqualTo(1.0),
                    () -> assertThat(t.matrix().get(inactive).get(inactive))
                            .as("из INACTIVE остаётся в INACTIVE").isEqualTo(1.0),
                    () -> assertThat(t.matrix().get(star).get(star)).isEqualTo(0.0));
        }

        @Test
        @DisplayName("Пустой вход → 4×4 нули, корректные тиры")
        void empty() {
            TierTransitions t = CohortAssembler.tierTransitions(List.of(), 1, 10.0);
            assertAll(
                    () -> assertThat(t.tiers()).hasSize(4),
                    () -> assertThat(t.matrix()).hasSize(4),
                    () -> assertThat(t.matrix()).allSatisfy(row ->
                            assertThat(row).containsExactly(0.0, 0.0, 0.0, 0.0)));
        }
    }
}
