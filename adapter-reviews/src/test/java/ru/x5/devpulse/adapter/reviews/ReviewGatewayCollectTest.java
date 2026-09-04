package ru.x5.devpulse.adapter.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.adapter.gitlab.GitRepoProperties;
import ru.x5.devpulse.adapter.gitlab.GitlabHttpClient;
import ru.x5.devpulse.adapter.gitlab.GitlabProperties;
import ru.x5.devpulse.adapter.gitlab.GitlabRateLimiter;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabApprovalsDto;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabMrDto;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabNoteDto;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabUserDto;
import ru.x5.devpulse.domain.model.review.CollectedMergeRequest;
import ru.x5.devpulse.domain.model.review.MrReview;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewGatewayAdapter — сбор и агрегация ревью (mock GitLab client)")
class ReviewGatewayCollectTest {

    // public_email есть; в MR/approvals/notes приходит «упрощённый» вид без email.
    private static final GitlabUserDto BORIS_FULL =
            new GitlabUserDto(88L, "Boris.Osechinskiy", "Osechinsky, Boris", "boris.osechinskiy@x5.ru");
    private static final GitlabUserDto BORIS_SIMPLE =
            new GitlabUserDto(88L, "Boris.Osechinskiy", "Osechinsky, Boris", null);
    private static final GitlabUserDto ALICE_FULL =
            new GitlabUserDto(99L, "Alice.A", "A, Alice", null); // нет public_email → fallback
    private static final GitlabUserDto ALICE_SIMPLE =
            new GitlabUserDto(99L, "Alice.A", "A, Alice", null);

    @Mock GitlabHttpClient http;
    @Mock GitlabRateLimiter rateLimiter;

    @Test
    @DisplayName("Резолв email (public_email/username), approve+комменты, отсев саморевью и системных")
    void collectsAndAggregates() {
        // rate-limiter просто выполняет переданный вызов
        when(rateLimiter.execute(any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        // /users: boris с public_email, alice без (одна страница)
        when(http.getUsers(1, 100, true)).thenReturn(List.of(BORIS_FULL, ALICE_FULL));

        // MR от boris, смержен; страница 2 пустая
        OffsetDateTime created = OffsetDateTime.of(2026, 5, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime merged = OffsetDateTime.of(2026, 5, 10, 14, 0, 0, 0, ZoneOffset.UTC);
        GitlabMrDto mr = new GitlabMrDto(1000L, 7L, 42L, "fix", "merged",
                "https://scm/mr/7", created, merged, "dev", BORIS_SIMPLE, 2);
        // 1 MR (< perPage) → пагинация останавливается на первой странице, page 2 не запрашивается.
        when(http.getMergeRequests(eq("grp/repo"), anyString(), eq(1), eq(100), eq("all"), eq("all")))
                .thenReturn(List.of(mr));

        // alice заапрувила
        when(http.getApprovals("grp/repo", 7L))
                .thenReturn(new GitlabApprovalsDto(List.of(new GitlabApprovalsDto.ApprovedBy(ALICE_SIMPLE))));

        // notes: alice (комментарий), boris (самокоммент — отсеять), системная (отсеять)
        when(http.getNotes("grp/repo", 7L, 1, 100)).thenReturn(List.of(
                new GitlabNoteDto(1L, ALICE_SIMPLE, false),
                new GitlabNoteDto(2L, BORIS_SIMPLE, false),
                new GitlabNoteDto(3L, ALICE_SIMPLE, true)));

        List<CollectedMergeRequest> result = new ArrayList<>();
        adapter().streamMergeRequests(LocalDateTime.of(2026, 5, 1, 0, 0), () -> false, result::addAll);

        assertThat(result).hasSize(1);
        CollectedMergeRequest c = result.getFirst();
        assertAll("собранный MR",
                () -> assertThat(c.gitlabProjectId()).isEqualTo(42L),
                () -> assertThat(c.gitlabMrIid()).isEqualTo(7L),
                () -> assertThat(c.author().value())
                        .as("boris — по public_email из /users").isEqualTo("boris.osechinskiy@x5.ru"),
                () -> assertThat(c.mergedAt()).isEqualTo(LocalDateTime.of(2026, 5, 10, 14, 0)),
                () -> assertThat(c.reviews()).as("только alice; boris (self) отсеян").hasSize(1));

        MrReview alice = c.reviews().getFirst();
        assertAll("ревью alice",
                () -> assertThat(alice.reviewer().value())
                        .as("alice — fallback username@домен").isEqualTo("alice.a@x5.ru"),
                () -> assertThat(alice.approved()).isTrue(),
                () -> assertThat(alice.commentCount())
                        .as("1 не-системный коммент (системный отсеян)").isEqualTo(1));
    }

    @Test
    @DisplayName("Падение одного MR не роняет остальные — собранные сохраняются, потеря не валит фазу (P1-2)")
    void oneFailingMrDoesNotDropOthers() {
        when(rateLimiter.execute(any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        when(http.getUsers(1, 100, true)).thenReturn(List.of(ALICE_FULL));

        OffsetDateTime created = OffsetDateTime.of(2026, 5, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        // user_notes_count=null — поля нет в ответе: notes запрашиваются, как раньше.
        GitlabMrDto good = new GitlabMrDto(1000L, 7L, 42L, "ok", "opened",
                "https://scm/mr/7", created, null, "dev", ALICE_SIMPLE, null);
        GitlabMrDto bad = new GitlabMrDto(1000L, 8L, 42L, "boom", "opened",
                "https://scm/mr/8", created, null, "dev", ALICE_SIMPLE, null);
        when(http.getMergeRequests(eq("grp/repo"), anyString(), eq(1), eq(100), eq("all"), eq("all")))
                .thenReturn(List.of(good, bad));

        // good — пустые approvals/notes: collected (без ревьюеров, но MR валиден)
        when(http.getApprovals("grp/repo", 7L)).thenReturn(new GitlabApprovalsDto(List.of()));
        when(http.getNotes("grp/repo", 7L, 1, 100)).thenReturn(List.of());
        // bad — approvals бросает: toCollected падает, MR теряется, но good остаётся
        when(http.getApprovals("grp/repo", 8L)).thenThrow(new RuntimeException("GitLab 500"));

        List<CollectedMergeRequest> result = new ArrayList<>();
        adapter().streamMergeRequests(LocalDateTime.of(2026, 5, 1, 0, 0), () -> false, result::addAll);

        assertAll("частичный сбор устойчив к падению одного MR",
                () -> assertThat(result).as("good собран, bad потерян — не весь батч").hasSize(1),
                () -> assertThat(result.getFirst().gitlabMrIid()).isEqualTo(7L));
    }

    @Test
    @DisplayName("MR без пользовательских комментариев (user_notes_count=0) — запрос notes не делается")
    void skipsNotesRequestWhenNoUserNotes() {
        when(rateLimiter.execute(any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        when(http.getUsers(1, 100, true)).thenReturn(List.of(BORIS_FULL, ALICE_FULL));

        OffsetDateTime created = OffsetDateTime.of(2026, 5, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        GitlabMrDto silent = new GitlabMrDto(1000L, 7L, 42L, "no discussion", "merged",
                "https://scm/mr/7", created, null, "dev", BORIS_SIMPLE, 0);
        when(http.getMergeRequests(eq("grp/repo"), anyString(), eq(1), eq(100), eq("all"), eq("all")))
                .thenReturn(List.of(silent));
        // approvals тянем по-прежнему: апрув без единого комментария — обычное дело.
        when(http.getApprovals("grp/repo", 7L))
                .thenReturn(new GitlabApprovalsDto(List.of(new GitlabApprovalsDto.ApprovedBy(ALICE_SIMPLE))));

        List<CollectedMergeRequest> result = new ArrayList<>();
        adapter().streamMergeRequests(LocalDateTime.of(2026, 5, 1, 0, 0), () -> false, result::addAll);

        verify(http, never()).getNotes(anyString(), anyLong(), anyInt(), anyInt());
        assertAll("MR собран без обращения за notes",
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.getFirst().reviews())
                        .as("апрув alice учтён и без notes").hasSize(1),
                () -> assertThat(result.getFirst().reviews().getFirst().commentCount())
                        .as("комментариев нет").isZero());
    }

    @Test
    @DisplayName("Крупный проект пишется чанками по ходу, а не одним батчем в конце")
    void flushesInChunksWithinProject() {
        when(rateLimiter.execute(any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        when(http.getUsers(1, 1000, true)).thenReturn(List.of(ALICE_FULL));

        // 600 MR — больше порога сброса (500), значит сбросов должно быть больше одного.
        // user_notes_count=0 у всех: тест про чанкование, notes тут только шумели бы.
        OffsetDateTime created = OffsetDateTime.of(2026, 5, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        List<GitlabMrDto> many = new ArrayList<>(600);
        for (int i = 1; i <= 600; i++) {
            many.add(new GitlabMrDto(1000L + i, (long) i, 42L, "mr " + i, "merged",
                    "https://scm/mr/" + i, created, created, "dev", ALICE_SIMPLE, 0));
        }
        // pageSize=1000 → 600 < perPage, пагинация останавливается на первой странице
        when(http.getMergeRequests(eq("grp/repo"), anyString(), eq(1), eq(1000), eq("all"), eq("all")))
                .thenReturn(many);
        when(http.getApprovals(eq("grp/repo"), anyLong()))
                .thenReturn(new GitlabApprovalsDto(List.of()));

        List<Integer> batchSizes = new ArrayList<>();
        adapter(1000).streamMergeRequests(
                LocalDateTime.of(2026, 5, 1, 0, 0), () -> false, b -> batchSizes.add(b.size()));

        assertAll("запись идёт частями, ничего не потеряно",
                () -> assertThat(batchSizes)
                        .as("сбросов больше одного — результат закрепляется по ходу проекта")
                        .hasSizeGreaterThan(1),
                () -> assertThat(batchSizes.stream().mapToInt(Integer::intValue).sum())
                        .as("суммарно отданы все 600 MR").isEqualTo(600),
                () -> assertThat(batchSizes.getFirst())
                        .as("первый чанк отдан по достижении порога, не в конце")
                        .isBetween(500, 599));
    }

    private ReviewGatewayAdapter adapter() {
        return adapter(100);
    }

    private ReviewGatewayAdapter adapter(int pageSize) {
        GitlabProperties props = new GitlabProperties(
                "https://scm/api/v4", "tok", List.of("grp/repo"), "x5.ru",
                true, 3650, 1, 0, 1, 0, pageSize, false, null, null, null);
        return new ReviewGatewayAdapter(http, rateLimiter, props, new GitRepoProperties(List.of()));
    }
}
