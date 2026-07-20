package ru.x5.devpulse.adapter.persistence.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReviewStatsRepositoryAdapter.repoName (web_url → namespace/repo)")
class ReviewStatsRepoNameTest {

    @Test
    @DisplayName("Обычный MR-url → namespace/repo без scheme/host и хвоста")
    void plainUrl() {
        assertThat(ReviewStatsRepositoryAdapter.repoName(
                "https://scm.x5.ru/gkr/xrg-markable/-/merge_requests/42", 10L))
                .isEqualTo("gkr/xrg-markable");
    }

    @Test
    @DisplayName("Вложенные подгруппы сохраняются целиком")
    void nestedSubgroups() {
        assertThat(ReviewStatsRepositoryAdapter.repoName(
                "https://scm.x5.ru/gkr/platform/team/svc/-/merge_requests/7", 11L))
                .isEqualTo("gkr/platform/team/svc");
    }

    @Test
    @DisplayName("web_url null/blank → фолбэк project-<id>")
    void nullFallback() {
        assertThat(ReviewStatsRepositoryAdapter.repoName(null, 55L)).isEqualTo("project-55");
        assertThat(ReviewStatsRepositoryAdapter.repoName("  ", 56L)).isEqualTo("project-56");
    }

    @Test
    @DisplayName("Без маркера /-/merge_requests — берём путь после хоста как есть")
    void noMarker() {
        assertThat(ReviewStatsRepositoryAdapter.repoName("https://scm.x5.ru/gkr/xrg-core", 12L))
                .isEqualTo("gkr/xrg-core");
    }
}
