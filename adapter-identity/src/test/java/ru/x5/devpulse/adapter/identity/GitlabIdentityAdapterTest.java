package ru.x5.devpulse.adapter.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
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
import ru.x5.devpulse.adapter.gitlab.dto.GitlabMemberDto;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitlabIdentityAdapter.maxProjectAccessLevel")
class GitlabIdentityAdapterTest {

    @Mock
    GitlabHttpClient http;

    private GitlabIdentityAdapter adapter(List<String> projects) {
        GitlabProperties props = new GitlabProperties(
                "https://scm/api/v4", "svc-tok", projects, "x5.ru",
                false, 0, 1, 0, 1, 0, 100, false, null, null, null);
        // gitlabUserRestClient не нужен для membership-логики → null.
        return new GitlabIdentityAdapter(null, http, props, new GitRepoProperties(List.of()));
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
}
