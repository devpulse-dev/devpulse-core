package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.model.review.MergeRequest;
import ru.x5.devpulse.domain.model.review.MrReview;
import ru.x5.devpulse.domain.model.review.ReviewAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;

@DisplayName("Domain service: ReviewSummarizer")
class ReviewSummarizerTest {

    private static final Email BORIS = new Email("boris@x5.ru");
    private static final Email ALICE = new Email("alice@x5.ru");
    private static final Email BOB = new Email("bob@x5.ru");

    @Test
    @DisplayName("Пустой вход → пустой результат")
    void empty() {
        assertThat(ReviewSummarizer.summarize(List.of())).isEmpty();
    }

    @Test
    @DisplayName("given/received агрегируются раздельно; avgTimeToMerge усредняется по смерженным")
    void aggregatesGivenAndReceived() {
        // MR1: boris, merged за 8ч, ревьюит alice (approve + 3 коммента)
        MergeRequest mr1 = new MergeRequest(BORIS,
                LocalDateTime.of(2026, 5, 10, 10, 0),
                LocalDateTime.of(2026, 5, 10, 18, 0),
                List.of(new MrReview(ALICE, true, 3)));

        // MR2: boris, merged за 4ч, ревьюят alice (НЕ approve, 2 коммента) + bob (approve, 0)
        MergeRequest mr2 = new MergeRequest(BORIS,
                LocalDateTime.of(2026, 5, 11, 9, 0),
                LocalDateTime.of(2026, 5, 11, 13, 0),
                List.of(new MrReview(ALICE, false, 2), new MrReview(BOB, true, 0)));

        // MR3: alice, не смержен, без ревью
        MergeRequest mr3 = new MergeRequest(ALICE,
                LocalDateTime.of(2026, 5, 12, 10, 0), null, List.of());

        Map<Email, ReviewAuthorStats> byEmail = ReviewSummarizer.summarize(List.of(mr1, mr2, mr3))
                .stream().collect(Collectors.toMap(ReviewAuthorStats::email, Function.identity()));

        assertAll("boris (автор 2 смерженных MR, ничего не ревьюил)",
                () -> assertThat(byEmail.get(BORIS).reviewsGiven()).isZero(),
                () -> assertThat(byEmail.get(BORIS).commentsGiven()).isZero(),
                () -> assertThat(byEmail.get(BORIS).reviewsReceived()).isEqualTo(2),
                () -> assertThat(byEmail.get(BORIS).mergedMrCount()).isEqualTo(2),
                () -> assertThat(byEmail.get(BORIS).avgTimeToMergeHours()).isCloseTo(6.0, within(1e-9)));

        assertAll("alice (approve 1 MR, 5 комментов, свой MR без ревью)",
                () -> assertThat(byEmail.get(ALICE).reviewsGiven()).isEqualTo(1),
                () -> assertThat(byEmail.get(ALICE).commentsGiven()).isEqualTo(5),
                () -> assertThat(byEmail.get(ALICE).reviewsReceived()).isZero(),
                () -> assertThat(byEmail.get(ALICE).mergedMrCount()).isZero(),
                () -> assertThat(byEmail.get(ALICE).avgTimeToMergeHours()).isZero());

        assertAll("bob (approve 1 MR, без комментов)",
                () -> assertThat(byEmail.get(BOB).reviewsGiven()).isEqualTo(1),
                () -> assertThat(byEmail.get(BOB).commentsGiven()).isZero(),
                () -> assertThat(byEmail.get(BOB).reviewsReceived()).isZero());
    }

    @Test
    @DisplayName("Сортировка: по убыванию вовлечённости (reviewsGiven + commentsGiven), tie-break email")
    void sortedByInvolvement() {
        MergeRequest mr = new MergeRequest(BORIS,
                LocalDateTime.of(2026, 5, 10, 10, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                List.of(new MrReview(ALICE, true, 5), new MrReview(BOB, true, 0)));

        List<ReviewAuthorStats> result = ReviewSummarizer.summarize(List.of(mr));

        // alice: 1+5=6, bob: 1+0=1, boris: 0 → alice, bob, boris
        assertThat(result).extracting(s -> s.email().value())
                .containsExactly("alice@x5.ru", "bob@x5.ru", "boris@x5.ru");
    }
}
