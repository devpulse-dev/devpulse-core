package ru.x5.markable.dev.analytics.domain.model.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RepoNameTest {

    @Test
    void parsesHttpsUrlWithGitSuffix() {
        assertThat(RepoName.fromUrl("https://scm.x5.ru/gkr/xrg-core.git").value())
                .isEqualTo("xrg-core");
    }

    @Test
    void parsesUrlWithoutGitSuffix() {
        assertThat(RepoName.fromUrl("https://scm.x5.ru/gkr/xrg-core").value())
                .isEqualTo("xrg-core");
    }

    @Test
    void parsesPlainName() {
        assertThat(RepoName.fromUrl("xrg-core.git").value()).isEqualTo("xrg-core");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new RepoName(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
