package ru.x5.devpulse.adapter.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
                "https://scm/mr/7", created, merged, BORIS_SIMPLE);
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

        List<CollectedMergeRequest> result = adapter().fetchMergeRequests(LocalDateTime.of(2026, 5, 1, 0, 0));

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
        GitlabMrDto good = new GitlabMrDto(1000L, 7L, 42L, "ok", "opened",
                "https://scm/mr/7", created, null, ALICE_SIMPLE);
        GitlabMrDto bad = new GitlabMrDto(1000L, 8L, 42L, "boom", "opened",
                "https://scm/mr/8", created, null, ALICE_SIMPLE);
        when(http.getMergeRequests(eq("grp/repo"), anyString(), eq(1), eq(100), eq("all"), eq("all")))
                .thenReturn(List.of(good, bad));

        // good — пустые approvals/notes: collected (без ревьюеров, но MR валиден)
        when(http.getApprovals("grp/repo", 7L)).thenReturn(new GitlabApprovalsDto(List.of()));
        when(http.getNotes("grp/repo", 7L, 1, 100)).thenReturn(List.of());
        // bad — approvals бросает: toCollected падает, MR теряется, но good остаётся
        when(http.getApprovals("grp/repo", 8L)).thenThrow(new RuntimeException("GitLab 500"));

        List<CollectedMergeRequest> result =
                adapter().fetchMergeRequests(LocalDateTime.of(2026, 5, 1, 0, 0));

        assertAll("частичный сбор устойчив к падению одного MR",
                () -> assertThat(result).as("good собран, bad потерян — не весь батч").hasSize(1),
                () -> assertThat(result.getFirst().gitlabMrIid()).isEqualTo(7L));
    }

    private ReviewGatewayAdapter adapter() {
        GitlabProperties props = new GitlabProperties(
                "https://scm/api/v4", "tok", List.of("grp/repo"), "x5.ru",
                true, 3650, 1, 0, 1, 0, 100, false, null, null, null);
        return new ReviewGatewayAdapter(http, rateLimiter, props, new GitRepoProperties(List.of()));
    }
}
