package ru.x5.devpulse.adapter.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("GitlabProjectPaths")
class GitlabProjectPathsTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource({
            "https://scm.x5.ru/gkr/xrg-core.git, gkr/xrg-core",
            "https://scm.x5.ru/gkr/xrg-markable.git, gkr/xrg-markable",
            "https://scm.x5.ru/group/sub/repo.git, group/sub/repo",
            "https://scm.x5.ru/gkr/xrg-core, gkr/xrg-core"
    })
    @DisplayName("toProjectPath: clone-URL → namespace/repo (без схемы/.git)")
    void derivesProjectPath(String url, String expected) {
        assertThat(GitlabProjectPaths.toProjectPath(url)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] пустое: \"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("toProjectPath: пустой/пробельный URL → null")
    void blankUrlReturnsNull(String url) {
        assertThat(GitlabProjectPaths.toProjectPath(url)).isNull();
    }

    @Test
    @DisplayName("toProjectPath: null → null")
    void nullUrlReturnsNull() {
        assertThat(GitlabProjectPaths.toProjectPath(null)).isNull();
    }

    @Test
    @DisplayName("resolve: gitlab.api.projects имеет приоритет над git.repositories")
    void resolvePrefersExplicitProjects() {
        var props = gitlabProps(List.of("grp/explicit"));
        var repos = new GitRepoProperties(List.of("https://scm.x5.ru/grp/derived.git"));
        assertThat(GitlabProjectPaths.resolve(props, repos)).containsExactly("grp/explicit");
    }

    @Test
    @DisplayName("resolve: пустой projects → дериват из git.repositories")
    void resolveDerivesFromRepos() {
        var props = gitlabProps(List.of());
        var repos = new GitRepoProperties(List.of(
                "https://scm.x5.ru/grp/a.git", "https://scm.x5.ru/grp/b.git"));
        assertThat(GitlabProjectPaths.resolve(props, repos)).containsExactly("grp/a", "grp/b");
    }

    private static GitlabProperties gitlabProps(List<String> projects) {
        return new GitlabProperties(
                "https://scm/api/v4", "tok", projects, "x5.ru",
                false, 0, 1, 0, 1, 0, 100, false, null, null, null);
    }
}
