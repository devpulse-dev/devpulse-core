package ru.x5.markable.dev.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

@DisplayName("Domain Service: AuthorAggregator")
class AuthorAggregatorTest {

    private static final Email BORIS = new Email("boris@x5.ru");
    private static final Email IVAN = new Email("ivan@x5.ru");
    private static final RepoName CORE = new RepoName("xrg-core");
    private static final RepoName MARKABLE = new RepoName("xrg-markable");
    private static final LocalDateTime JAN_1_MORNING = LocalDateTime.of(2026, 1, 1, 9, 0);
    private static final LocalDateTime JAN_1_EVENING = LocalDateTime.of(2026, 1, 1, 21, 0);
    private static final LocalDateTime JAN_2_MIDDAY = LocalDateTime.of(2026, 1, 2, 12, 0);

    @Test
    @DisplayName("Группирует коммиты по ключу (email, день, репозиторий) и суммирует метрики")
    void groupsByEmailDateAndRepoAndSumsMetrics() {
        List<Commit> commits = List.of(
                commit("a".repeat(40), BORIS, JAN_1_MORNING, false, 10, 5, 0, CORE),
                commit("b".repeat(40), BORIS, JAN_1_EVENING, false, 20, 1, 5, CORE),
                commit("c".repeat(40), BORIS, JAN_1_MORNING, true, 0, 0, 0, CORE),
                commit("d".repeat(40), BORIS, JAN_2_MIDDAY,  false, 7, 2, 0, CORE),
                commit("e".repeat(40), IVAN,  JAN_1_MORNING, false, 1, 0, 0, CORE),
                commit("f".repeat(40), BORIS, JAN_1_MORNING, false, 3, 1, 0, MARKABLE)
        );

        List<DailyAuthorStats> result = AuthorAggregator.aggregateByDay(commits);

        DailyAuthorStats borisJan1Core = pick(result, BORIS, CORE, 1);
        DailyAuthorStats borisJan2Core = pick(result, BORIS, CORE, 2);

        assertAll("агрегация по 6 коммитам должна дать 4 уникальных ключа",
                () -> assertThat(result)
                        .as("4 уникальных ключа: boris/jan1/core, boris/jan2/core, ivan/jan1/core, boris/jan1/markable")
                        .hasSize(4),

                () -> assertThat(borisJan1Core.commits())
                        .as("boris/jan1/core: всего 3 коммита (2 обычных + 1 merge)")
                        .isEqualTo(3),
                () -> assertThat(borisJan1Core.mergeCommits())
                        .as("boris/jan1/core: один merge-коммит")
                        .isEqualTo(1),
                () -> assertThat(borisJan1Core.addedLines())
                        .as("boris/jan1/core: сумма добавленных = 10+20+0 = 30")
                        .isEqualTo(30),
                () -> assertThat(borisJan1Core.deletedLines())
                        .as("boris/jan1/core: сумма удалённых = 5+1+0 = 6")
                        .isEqualTo(6),
                () -> assertThat(borisJan1Core.testAddedLines())
                        .as("boris/jan1/core: только b-коммит добавил 5 тестовых строк")
                        .isEqualTo(5),

                () -> assertThat(borisJan2Core.commits())
                        .as("boris/jan2/core: один коммит d")
                        .isEqualTo(1),
                () -> assertThat(borisJan2Core.addedLines())
                        .as("boris/jan2/core: 7 добавленных строк")
                        .isEqualTo(7));
    }

    @Nested
    @DisplayName("Граничные случаи входа")
    class EdgeCases {

        static Collection<List<Commit>> emptyInputs() {
            return List.of(List.<Commit>of());
        }

        @ParameterizedTest(name = "[{index}] вход: {0}")
        @NullSource
        @MethodSource("emptyInputs")
        @DisplayName("Пустой/null вход → пустой список агрегатов")
        void emptyInputProducesEmptyOutput(List<Commit> input) {
            assertThat(AuthorAggregator.aggregateByDay(input))
                    .as("на пустом входе не должно быть агрегатов")
                    .isEmpty();
        }
    }

    private static Commit commit(String hash, Email author, LocalDateTime date,
                                 boolean merge, long added, long deleted, long testAdded,
                                 RepoName repo) {
        return new Commit(new CommitHash(hash), author, date, merge,
                added, deleted, testAdded, "msg", null, repo);
    }

    private static DailyAuthorStats pick(List<DailyAuthorStats> all, Email email, RepoName repo, int day) {
        return all.stream()
                .filter(s -> s.authorEmail().equals(email)
                        && s.repo().equals(repo)
                        && s.date().getDayOfMonth() == day)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "не найдено: email=%s repo=%s день=%d".formatted(email, repo, day)));
    }
}
