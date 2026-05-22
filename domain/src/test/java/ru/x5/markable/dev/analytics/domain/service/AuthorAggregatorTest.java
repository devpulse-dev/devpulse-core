package ru.x5.markable.dev.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.git.CommitHash;
import ru.x5.markable.dev.analytics.domain.model.git.RepoName;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

class AuthorAggregatorTest {

    private static final Email BORIS = new Email("boris@x5.ru");
    private static final Email IVAN = new Email("ivan@x5.ru");
    private static final RepoName CORE = new RepoName("xrg-core");
    private static final RepoName MARKABLE = new RepoName("xrg-markable");

    @Test
    void groupsByEmailDateAndRepo() {
        LocalDateTime jan1morning = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime jan1evening = LocalDateTime.of(2026, 1, 1, 21, 0);
        LocalDateTime jan2 = LocalDateTime.of(2026, 1, 2, 12, 0);

        List<Commit> commits = List.of(
                commit("a".repeat(40), BORIS, jan1morning, false, 10, 5, 0, CORE),
                commit("b".repeat(40), BORIS, jan1evening, false, 20, 1, 5, CORE),     // same key as ↑
                commit("c".repeat(40), BORIS, jan1morning, true, 0, 0, 0, CORE),       // merge same day
                commit("d".repeat(40), BORIS, jan2,        false, 7, 2, 0, CORE),      // next day
                commit("e".repeat(40), IVAN, jan1morning,  false, 1, 0, 0, CORE),      // different author
                commit("f".repeat(40), BORIS, jan1morning, false, 3, 1, 0, MARKABLE)   // different repo
        );

        List<DailyAuthorStats> stats = AuthorAggregator.aggregateByDay(commits);

        assertThat(stats).hasSize(4);

        DailyAuthorStats borisJan1Core = pick(stats, BORIS, CORE, 1);
        assertThat(borisJan1Core.commits()).isEqualTo(3);
        assertThat(borisJan1Core.mergeCommits()).isEqualTo(1);
        assertThat(borisJan1Core.addedLines()).isEqualTo(30);
        assertThat(borisJan1Core.deletedLines()).isEqualTo(6);
        assertThat(borisJan1Core.testAddedLines()).isEqualTo(5);

        DailyAuthorStats borisJan2Core = pick(stats, BORIS, CORE, 2);
        assertThat(borisJan2Core.commits()).isEqualTo(1);
        assertThat(borisJan2Core.addedLines()).isEqualTo(7);
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertThat(AuthorAggregator.aggregateByDay(List.of())).isEmpty();
        assertThat(AuthorAggregator.aggregateByDay(null)).isEmpty();
    }

    private static Commit commit(String hash, Email author, LocalDateTime date,
                                 boolean merge, long added, long deleted, long testAdded,
                                 RepoName repo) {
        return new Commit(
                new CommitHash(hash),
                author,
                date,
                merge,
                added,
                deleted,
                testAdded,
                "msg",
                null,
                repo
        );
    }

    private static DailyAuthorStats pick(List<DailyAuthorStats> all, Email email, RepoName repo, int day) {
        return all.stream()
                .filter(s -> s.authorEmail().equals(email)
                        && s.repo().equals(repo)
                        && s.date().getDayOfMonth() == day)
                .findFirst()
                .orElseThrow();
    }
}
