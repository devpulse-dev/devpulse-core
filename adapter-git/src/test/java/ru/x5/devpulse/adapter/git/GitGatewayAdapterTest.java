package ru.x5.devpulse.adapter.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.RepoName;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitGatewayAdapter: streaming + батчинг по BATCH_SIZE")
class GitGatewayAdapterTest {

    private static final String REPO_URL = "https://scm.x5.ru/gkr/xrg-core.git";
    private static final RepoName REPO = RepoName.fromUrl(REPO_URL);
    private static final Path REPO_PATH = Path.of("/tmp/git-cache/xrg-core");
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 1, 0, 0);
    private static final LocalDateTime UNTIL = LocalDateTime.of(2026, 5, 31, 0, 0);

    @Mock GitCliClient cli;

    private GitGatewayAdapter newAdapter() {
        GitProperties props = new GitProperties(
                List.of(REPO_URL),
                null,
                Path.of("/tmp/git-cache"),
                Duration.ofMinutes(5));
        return new GitGatewayAdapter(props, cli);
    }

    @Test
    @DisplayName("Меньше BATCH_SIZE коммитов → один батч (хвостовой flush на finish)")
    void smallStreamFlushesOnceAtEnd() throws Exception {
        when(cli.prepare(eq(REPO_URL), any())).thenReturn(new GitCliClient.PreparedRepo(REPO, REPO_PATH));
        stubStreamLog(generateLogLines(3));

        GitGatewayAdapter adapter = newAdapter();
        List<List<Commit>> batches = new ArrayList<>();
        adapter.streamCommits(REPO, SINCE, UNTIL, batches::add, () -> false);

        assertAll("один хвостовой батч из 3 коммитов",
                () -> assertThat(batches).hasSize(1),
                () -> assertThat(batches.getFirst()).hasSize(3));
    }

    @Test
    @DisplayName("Ровно BATCH_SIZE * N коммитов → N батчей по BATCH_SIZE, ни одного хвостового")
    void exactMultipleProducesExactBatches() throws Exception {
        when(cli.prepare(eq(REPO_URL), any())).thenReturn(new GitCliClient.PreparedRepo(REPO, REPO_PATH));
        int total = GitGatewayAdapter.BATCH_SIZE * 2;
        stubStreamLog(generateLogLines(total));

        GitGatewayAdapter adapter = newAdapter();
        List<List<Commit>> batches = new ArrayList<>();
        adapter.streamCommits(REPO, SINCE, UNTIL, batches::add, () -> false);

        assertAll("ровно 2 батча",
                () -> assertThat(batches).hasSize(2),
                () -> assertThat(batches.get(0)).hasSize(GitGatewayAdapter.BATCH_SIZE),
                () -> assertThat(batches.get(1)).hasSize(GitGatewayAdapter.BATCH_SIZE));
    }

    @Test
    @DisplayName("BATCH_SIZE + 1 коммитов → один полный батч + один хвостовой из 1")
    void overflowProducesTailBatch() throws Exception {
        when(cli.prepare(eq(REPO_URL), any())).thenReturn(new GitCliClient.PreparedRepo(REPO, REPO_PATH));
        int total = GitGatewayAdapter.BATCH_SIZE + 1;
        stubStreamLog(generateLogLines(total));

        GitGatewayAdapter adapter = newAdapter();
        List<List<Commit>> batches = new ArrayList<>();
        adapter.streamCommits(REPO, SINCE, UNTIL, batches::add, () -> false);

        assertAll("полный батч + хвост",
                () -> assertThat(batches).hasSize(2),
                () -> assertThat(batches.get(0)).hasSize(GitGatewayAdapter.BATCH_SIZE),
                () -> assertThat(batches.get(1)).hasSize(1));
    }

    @Test
    @DisplayName("Пустой output → batchHandler НЕ зовётся ни разу")
    void emptyOutputDoesNotCallHandler() throws Exception {
        when(cli.prepare(eq(REPO_URL), any())).thenReturn(new GitCliClient.PreparedRepo(REPO, REPO_PATH));
        stubStreamLog(List.of());

        GitGatewayAdapter adapter = newAdapter();
        List<List<Commit>> batches = new ArrayList<>();
        adapter.streamCommits(REPO, SINCE, UNTIL, batches::add, () -> false);

        assertThat(batches).isEmpty();
    }

    @Test
    @DisplayName("CLI бросает InterruptedException → GitOperationInterruptedException + interrupt flag")
    void interruptIsPropagated() throws Exception {
        when(cli.prepare(eq(REPO_URL), any())).thenThrow(new InterruptedException("ctrl-c"));
        // сбрасываем флаг от предыдущих тестов
        Thread.interrupted();

        GitGatewayAdapter adapter = newAdapter();
        try {
            adapter.streamCommits(REPO, SINCE, UNTIL, b -> {}, () -> false);
            assertThat(false).as("должен был кинуть").isTrue();
        } catch (GitOperationInterruptedException expected) {
            assertThat(Thread.interrupted())
                    .as("interrupt flag должен быть восстановлен")
                    .isTrue();
        }
    }

    /* ---------- helpers ---------- */

    /**
     * Стабит {@code cli.streamLog}: при вызове отдаёт пасcед строки построчно в lineHandler.
     */
    private void stubStreamLog(List<String> lines) throws Exception {
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<String> handler = inv.getArgument(3);
            lines.forEach(handler::accept);
            return null;
        }).when(cli).streamLog(eq(REPO_PATH), any(), any(), any(), any());
    }

    /**
     * Генерирует валидные log-строки для N коммитов: header + один numstat на каждый.
     */
    private static List<String> generateLogLines(int n) {
        List<String> out = new ArrayList<>(n * 2);
        for (int i = 0; i < n; i++) {
            String sha = String.format("%040x", i + 1);
            out.add(sha + "|dev" + i + "@x5.ru|p|2026-05-15T12:00:00+03:00|TASK-" + i + " msg");
            out.add("1\t0\tsrc/main/java/F" + i + ".java");
        }
        return out;
    }
}
