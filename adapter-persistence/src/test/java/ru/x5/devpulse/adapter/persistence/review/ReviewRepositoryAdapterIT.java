package ru.x5.devpulse.adapter.persistence.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.x5.devpulse.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.ReviewWriteRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.CollectedMergeRequest;
import ru.x5.devpulse.domain.model.review.MergeRequest;
import ru.x5.devpulse.domain.model.review.MrReview;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * IT review-репозиториев. БД общая между тестами (как и в других IT проекта — без cleanup),
 * поэтому каждый тест использует <b>уникального автора</b> и фильтрует прочитанное по нему,
 * а не полагается на глобальный размер выборки.
 */
@SpringBootTest
@DisplayName("Review-репозитории (write + read, TestContainers + PostgreSQL)")
class ReviewRepositoryAdapterIT extends PostgresContainerSupport {

    private static final Email ALICE = new Email("alice-r@x5.ru");
    private static final Email BOB = new Email("bob-r@x5.ru");
    private static final Period MAY = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 5, 10, 10, 0);

    @Autowired ReviewWriteRepository writeRepo;
    @Autowired ReviewStatsRepository readRepo;

    @Test
    @DisplayName("upsert пишет MR + ревью; read возвращает их с approve/commentCount/mergedAt")
    void upsertThenRead() {
        Email author = new Email("mr-read@x5.ru");
        writeRepo.upsert(List.of(mr(42L, 7L, author, CREATED.plusHours(4), List.of(
                new MrReview(ALICE, true, 2),
                new MrReview(BOB, false, 1)))));

        MergeRequest m = readByAuthor(author);
        Map<Email, MrReview> reviews = m.reviews().stream()
                .collect(Collectors.toMap(MrReview::reviewer, Function.identity()));
        assertAll("записано и прочитано",
                () -> assertThat(m.isMerged()).isTrue(),
                () -> assertThat(m.reviews()).hasSize(2),
                () -> assertThat(reviews.get(ALICE).approved()).isTrue(),
                () -> assertThat(reviews.get(ALICE).commentCount()).isEqualTo(2),
                () -> assertThat(reviews.get(BOB).approved()).isFalse(),
                () -> assertThat(reviews.get(BOB).commentCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("Повторный upsert того же MR идемпотентен: не дублит MR, ревью заменяются")
    void upsertIsIdempotentAndReplacesReviews() {
        Email author = new Email("mr-idem@x5.ru");
        writeRepo.upsert(List.of(mr(42L, 99L, author, CREATED.plusHours(2), List.of(
                new MrReview(ALICE, true, 2),
                new MrReview(BOB, false, 1)))));

        // Тот же (project, iid), но ревью изменились: только alice, больше комментов, bob ушёл.
        writeRepo.upsert(List.of(mr(42L, 99L, author, CREATED.plusHours(2), List.of(
                new MrReview(ALICE, true, 5)))));

        List<MergeRequest> mine = readRepo.findMergeRequestsByPeriod(MAY).stream()
                .filter(m -> m.author().equals(author))
                .toList();

        assertAll("идемпотентность",
                () -> assertThat(mine).as("MR не задублился").hasSize(1),
                () -> assertThat(mine.getFirst().reviews()).as("ревью заменены").hasSize(1),
                () -> assertThat(mine.getFirst().reviews().getFirst().reviewer()).isEqualTo(ALICE),
                () -> assertThat(mine.getFirst().reviews().getFirst().commentCount()).isEqualTo(5));
    }

    private MergeRequest readByAuthor(Email author) {
        return readRepo.findMergeRequestsByPeriod(MAY).stream()
                .filter(m -> m.author().equals(author))
                .findFirst()
                .orElseThrow(() -> new AssertionError("MR автора " + author.value() + " не найден"));
    }

    private static CollectedMergeRequest mr(long projectId, long iid, Email author,
                                            LocalDateTime mergedAt, List<MrReview> reviews) {
        return new CollectedMergeRequest(
                projectId, iid, author, "title", "https://scm/mr/" + iid, "merged",
                CREATED, mergedAt, reviews);
    }
}
