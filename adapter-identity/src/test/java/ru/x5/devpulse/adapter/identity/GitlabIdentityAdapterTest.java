package ru.x5.devpulse.adapter.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import ru.x5.devpulse.adapter.gitlab.GitRepoProperties;
import ru.x5.devpulse.adapter.gitlab.GitlabHttpClient;
import ru.x5.devpulse.adapter.gitlab.GitlabProperties;
import ru.x5.devpulse.adapter.gitlab.GitlabRateLimiter;
import ru.x5.devpulse.adapter.gitlab.dto.GitlabMemberDto;
import ru.x5.devpulse.application.port.out.GitUnavailableException;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitlabIdentityAdapter.maxProjectAccessLevel")
class GitlabIdentityAdapterTest {

    @Mock GitlabHttpClient http;
    @Mock GitlabRateLimiter rateLimiter;

    private GitlabIdentityAdapter adapter(List<String> projects) {
        // rateLimiter просто выполняет переданный вызов (throttle/retry тут не тестируем).
        lenient().when(rateLimiter.execute(any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        GitlabProperties props = new GitlabProperties(
                "https://scm/api/v4", "svc-tok", projects, "x5.ru",
                false, 0, 1, 0, 1, 0, 100, false, null, null, null);
        // gitlabUserRestClient не нужен для membership-логики → null.
        return new GitlabIdentityAdapter(null, http, rateLimiter, props, new GitRepoProperties(List.of()));
    }

    private static HttpClientErrorException.NotFound notFound() {
        return (HttpClientErrorException.NotFound) HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null);
    }

    @Test
    @DisplayName("max access_level по проектам; не-участники (404) пропускаются")
    void maxAcrossProjectsSkippingNonMembers() {
        var adapter = adapter(List.of("grp/a", "grp/b", "grp/c"));
        when(http.getProjectMember("grp/a", 42L)).thenReturn(new GitlabMemberDto(42L, 20)); // Reporter
        when(http.getProjectMember("grp/b", 42L)).thenThrow(notFound());                    // не участник
        when(http.getProjectMember("grp/c", 42L)).thenReturn(new GitlabMemberDto(42L, 40)); // Maintainer

        assertThat(adapter.maxProjectAccessLevel(42)).isEqualTo(40);
    }

    @Test
    @DisplayName("ни в одном проекте не участник → 0")
    void noMembershipReturnsZero() {
        var adapter = adapter(List.of("grp/a"));
        when(http.getProjectMember("grp/a", 7L)).thenThrow(notFound());

        assertThat(adapter.maxProjectAccessLevel(7)).isZero();
    }

    @Test
    @DisplayName("429/5xx/сеть НЕ занижают доступ, а сигналят GitUnavailable (легитимному не откажут)")
    void unavailableDoesNotSilentlyLowerAccess() {
        var adapter = adapter(List.of("grp/a"));
        when(http.getProjectMember("grp/a", 9L)).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "429", HttpHeaders.EMPTY, null, null));

        assertThatThrownBy(() -> adapter.maxProjectAccessLevel(9))
                .as("проверить доступ не удалось ≠ нет доступа — не глушим в 0")
                .isInstanceOf(GitUnavailableException.class);
    }

    @Test
    @DisplayName("early-exit: найден Owner (50) — остальные проекты не опрашиваются")
    void earlyExitOnOwner() {
        var adapter = adapter(List.of("grp/a", "grp/b"));
        when(http.getProjectMember("grp/a", 5L)).thenReturn(new GitlabMemberDto(5L, 50)); // Owner

        assertThat(adapter.maxProjectAccessLevel(5)).isEqualTo(50);
        verify(http, never()).getProjectMember(eq("grp/b"), any(Long.class));
    }
}
