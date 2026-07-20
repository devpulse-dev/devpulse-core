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
import ru.x5.devpulse.domain.model.review.MergedMrCountRow;
import ru.x5.devpulse.domain.model.review.MrReview;
import ru.x5.devpulse.domain.model.review.RepoMergedMrCount;
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

    @Test
    @DisplayName("Batch-lookup (P1-3): два проекта с одинаковым iid в одном чанке не путаются")
    void batchLookupDistinguishesSameIidAcrossProjects() {
        Email authorA = new Email("p13-a@x5.ru");
        Email authorB = new Email("p13-b@x5.ru");
        // Одинаковый iid=5, разные project_id — суперсет batch-запроса должен фильтроваться по паре.
        writeRepo.upsert(List.of(
                mr(1300L, 5L, authorA, CREATED.plusHours(1), List.of(new MrReview(ALICE, true, 1))),
                mr(1301L, 5L, authorB, CREATED.plusHours(1), List.of(new MrReview(BOB, true, 9)))));

        // Повторный upsert только проекта 1300 — проект 1301 не должен задеться.
        writeRepo.upsert(List.of(
                mr(1300L, 5L, authorA, CREATED.plusHours(1), List.of(new MrReview(ALICE, true, 4)))));

        MergeRequest a = readByAuthor(authorA);
        MergeRequest b = readByAuthor(authorB);
        assertAll("composite-key изоляция",
                () -> assertThat(a.reviews()).as("проект 1300 обновлён").hasSize(1),
                () -> assertThat(a.reviews().getFirst().commentCount()).isEqualTo(4),
                () -> assertThat(b.reviews()).as("проект 1301 не задет").hasSize(1),
                () -> assertThat(b.reviews().getFirst().reviewer()).isEqualTo(BOB),
                () -> assertThat(b.reviews().getFirst().commentCount()).isEqualTo(9));
    }

    @Test
    @DisplayName("countMergedMrByAuthor: группировка по автору + фильтры период/ветки/авторы")
    void countsMergedByAuthor() {
        Email x = new Email("agg-author-x@x5.ru");
        Email y = new Email("agg-author-y@x5.ru");
        Email z = new Email("agg-author-z@x5.ru"); // не в запросе
        writeRepo.upsert(List.of(
                merged(9100L, 1L, x, mayDay(11), "dev"),
                merged(9100L, 2L, x, mayDay(12), "main"),
                merged(9100L, 3L, x, mayDay(13), "feature/x"),   // не dev-ветка → исключить
                merged(9100L, 4L, x, LocalDateTime.of(2026, 6, 15, 12, 0), "dev"), // merged вне периода
                merged(9100L, 5L, y, mayDay(14), "dev"),
                merged(9100L, 6L, z, mayDay(15), "dev")));       // автор не в запросе → исключить

        List<MergedMrCountRow> rows =
                readRepo.countMergedMrByAuthor(MAY, List.of(x, y), List.of("dev", "main"));
        Map<Email, Long> byEmail = rows.stream()
                .collect(Collectors.toMap(MergedMrCountRow::email, MergedMrCountRow::count));

        assertAll("агрегат по авторам",
                () -> assertThat(byEmail).containsOnlyKeys(x, y),
                () -> assertThat(byEmail.get(x)).as("dev + main, без feature/вне периода").isEqualTo(2L),
                () -> assertThat(byEmail.get(y)).isEqualTo(1L));
    }

    @Test
    @DisplayName("countMergedMrByRepo: группировка по проекту, имя репо из web_url, те же фильтры")
    void countsMergedByRepo() {
        Email a = new Email("agg-repo-a@x5.ru");
        writeRepo.upsert(List.of(
                mergedUrl(9201L, 11L, a, mayDay(11), "dev", "https://scm.x5.ru/gkr/core/-/merge_requests/11"),
                mergedUrl(9201L, 12L, a, mayDay(12), "main", "https://scm.x5.ru/gkr/core/-/merge_requests/12"),
                mergedUrl(9202L, 13L, a, mayDay(13), "dev", "https://scm.x5.ru/gkr/markable/-/merge_requests/13"),
                mergedUrl(9203L, 14L, a, mayDay(14), "feature/z", "https://scm.x5.ru/gkr/other/-/merge_requests/14")));

        List<RepoMergedMrCount> rows =
                readRepo.countMergedMrByRepo(MAY, List.of(a), List.of("dev", "main"));
        Map<String, Integer> byRepo = rows.stream()
                .collect(Collectors.toMap(RepoMergedMrCount::repo, RepoMergedMrCount::count));

        assertAll("агрегат по репозиториям",
                () -> assertThat(byRepo).as("feature/z-ветка исключена").containsOnlyKeys("gkr/core", "gkr/markable"),
                () -> assertThat(byRepo.get("gkr/core")).isEqualTo(2),
                () -> assertThat(byRepo.get("gkr/markable")).isEqualTo(1));
    }

    @Test
    @DisplayName("Пустой список авторов или веток → пустой результат (без запроса в БД)")
    void emptyInputsYieldEmpty() {
        Email a = new Email("agg-empty@x5.ru");
        assertAll(
                () -> assertThat(readRepo.countMergedMrByAuthor(MAY, List.of(), List.of("dev"))).isEmpty(),
                () -> assertThat(readRepo.countMergedMrByAuthor(MAY, List.of(a), List.of())).isEmpty(),
                () -> assertThat(readRepo.countMergedMrByRepo(MAY, List.of(), List.of("dev"))).isEmpty(),
                () -> assertThat(readRepo.countMergedMrByRepo(MAY, List.of(a), List.of())).isEmpty());
    }

    private MergeRequest readByAuthor(Email author) {
        return readRepo.findMergeRequestsByPeriod(MAY).stream()
                .filter(m -> m.author().equals(author))
                .findFirst()
                .orElseThrow(() -> new AssertionError("MR автора " + author.value() + " не найден"));
    }

    private static LocalDateTime mayDay(int day) {
        return LocalDateTime.of(2026, 5, day, 12, 0);
    }

    private static CollectedMergeRequest mr(long projectId, long iid, Email author,
                                            LocalDateTime mergedAt, List<MrReview> reviews) {
        return new CollectedMergeRequest(
                projectId, iid, author, "title", "https://scm/mr/" + iid, "merged",
                CREATED, mergedAt, "dev", reviews);
    }

    private static CollectedMergeRequest merged(long projectId, long iid, Email author,
                                                LocalDateTime mergedAt, String branch) {
        return mergedUrl(projectId, iid, author, mergedAt, branch, "https://scm/mr/" + iid);
    }

    private static CollectedMergeRequest mergedUrl(long projectId, long iid, Email author,
                                                   LocalDateTime mergedAt, String branch, String webUrl) {
        return new CollectedMergeRequest(
                projectId, iid, author, "title", webUrl, "merged", CREATED, mergedAt, branch, List.of());
    }
}
