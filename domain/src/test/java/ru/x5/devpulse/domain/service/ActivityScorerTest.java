package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.x5.devpulse.domain.model.stats.ActivityCategory;
import ru.x5.devpulse.domain.model.stats.ActivityScore;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.user.Email;

@DisplayName("ActivityScorer: volume × quality, категории по score")
class ActivityScorerTest {

    private static final double EXPECTED = 50.0;

    @ParameterizedTest(name = "[{index}] commits={1} avg-lines={2} → ~{3} ({4})")
    @CsvSource({
            // commits, mergeCommits, avgLines (через addedLines), ожидаемый score, категория
            // 50 коммитов по 100 строк = норма, score≈1.0 → ACTIVE
            "norm,            50, 0, 100, 1.0,  ACTIVE",
            // 75 коммитов с нормальной длиной → 1.5 → STAR (на границе)
            "star,            75, 0, 100, 1.5,  STAR",
            // 10 коммитов нормальной длины → 0.2 → BELOW_AVERAGE (на границе)
            "below_avg_edge,  10, 0, 100, 0.2,  BELOW_AVERAGE",
            // 5 коммитов норм → 0.1 → INACTIVE
            "inactive,         5, 0, 100, 0.1,  INACTIVE",
            // 50 коммитов по 2 строки (микро) → quality≈0.46 → score≈0.46 → BELOW_AVERAGE
            "micro_spam,      50, 0,   2, 0.46, BELOW_AVERAGE"
    })
    @DisplayName("score = volumeFactor × qualityFactor, категория по диапазону")
    void scoringMatrix(String label, long commits, long merges, long avgLines,
                       double expectedScore, ActivityCategory expectedCategory) {
        long totalLines = commits * avgLines;
        AuthorSummary author = new AuthorSummary(
                new Email("a@x5.ru"), null, null,
                commits, merges,
                /*added*/ totalLines, /*deleted*/ 0, /*test*/ 0);

        ActivityScore score = ActivityScorer.score(author, EXPECTED);

        assertAll(label,
                () -> assertThat(score.score()).as("score").isCloseTo(expectedScore,
                        org.assertj.core.data.Offset.offset(0.05)),
                () -> assertThat(score.category()).as("category").isEqualTo(expectedCategory));
    }

    @Test
    @DisplayName("Нет коммитов → score 0, INACTIVE, qualityFactor возвращает 0.3 (как для пустой работы)")
    void noCommits() {
        AuthorSummary empty = new AuthorSummary(
                new Email("ghost@x5.ru"), null, null,
                0, 0, 0, 0, 0);

        ActivityScore score = ActivityScorer.score(empty, EXPECTED);

        assertAll("пустой автор",
                () -> assertThat(score.score()).isZero(),
                () -> assertThat(score.category()).isEqualTo(ActivityCategory.INACTIVE),
                () -> assertThat(score.avgLinesPerCommit()).isZero());
    }

    @Test
    @DisplayName("Мерджи не считаются в volume — у автора 100 мерджей и 0 рабочих коммитов остаётся INACTIVE")
    void mergesAreNotCountedAsActivity() {
        AuthorSummary teamlead = new AuthorSummary(
                new Email("lead@x5.ru"), null, null,
                /*commits*/ 100, /*merges*/ 100,
                /*added*/ 0, /*deleted*/ 0, /*test*/ 0);

        ActivityScore score = ActivityScorer.score(teamlead, EXPECTED);

        assertAll("100 мерджей, 0 не-мердж",
                () -> assertThat(score.volumeFactor()).as("volume = 0").isZero(),
                () -> assertThat(score.category()).isEqualTo(ActivityCategory.INACTIVE));
    }

    @Test
    @DisplayName("Бомба-коммиты (>500 строк/коммит) штрафуются по quality")
    void bigBangCommitsPenalized() {
        AuthorSummary boomer = new AuthorSummary(
                new Email("boomer@x5.ru"), null, null,
                /*commits*/ 50, /*merges*/ 0,
                /*added*/ 50_000, /*deleted*/ 0, /*test*/ 0); // 1000 строк/коммит

        ActivityScore score = ActivityScorer.score(boomer, EXPECTED);

        assertAll("бомбы",
                () -> assertThat(score.volumeFactor()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01)),
                () -> assertThat(score.qualityFactor())
                        .as("при 1000 строк/коммит quality в диапазоне 0.7–0.8")
                        .isBetween(0.6, 0.8),
                () -> assertThat(score.score())
                        .as("score = volume × quality < 1.0 несмотря на полную норму по объёму")
                        .isLessThan(1.0));
    }

    @Test
    @DisplayName("Норма (50 коммитов / 30 дней) даёт score=1.0 при здоровой длине коммита")
    void exactNormGivesScoreOne() {
        AuthorSummary normalDev = new AuthorSummary(
                new Email("dev@x5.ru"), null, null,
                /*commits*/ 50, /*merges*/ 0,
                /*added*/ 2500, /*deleted*/ 0, /*test*/ 0); // 50 строк/коммит — здорово

        ActivityScore score = ActivityScorer.score(normalDev, EXPECTED);

        assertAll("норма",
                () -> assertThat(score.score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01)),
                () -> assertThat(score.category()).isEqualTo(ActivityCategory.ACTIVE),
                () -> assertThat(score.qualityFactor()).isEqualTo(1.0));
    }
}
