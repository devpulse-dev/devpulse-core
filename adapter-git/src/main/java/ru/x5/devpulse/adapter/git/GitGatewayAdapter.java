package ru.x5.devpulse.adapter.git;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.application.port.out.GitGateway;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.RepoName;

/**
 * Реализация {@link GitGateway} поверх локального git CLI.
 *
 * <p><b>Streaming-семантика:</b> {@link #streamCommits} читает {@code git log} построчно,
 * парсит инкрементально через {@link GitLogParser.Streaming} и отдаёт батчи по
 * {@link #BATCH_SIZE} коммитов в {@code batchHandler}. Память O(batch + 1 коммит-в-парсинге)
 * — не зависит от размера репозитория. На репо в миллион коммитов мы НЕ упрёмся в heap.</p>
 *
 * <p>Финальный остаток (&lt; BATCH_SIZE) flush'ится последним вызовом {@code batchHandler}.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class GitGatewayAdapter implements GitGateway {

    /**
     * Размер батча для flush в {@code batchHandler}. Подобран эмпирически: достаточно большой
     * чтобы амортизировать накладные расходы JDBC batch insert, но не настолько большой,
     * чтобы держать в памяти много объектов.
     */
    static final int BATCH_SIZE = 500;

    private final GitProperties properties;
    private final GitCliClient cli;

    @Override
    public List<RepoName> configuredRepos() {
        return properties.repositories().stream()
                .map(RepoName::fromUrl)
                .toList();
    }

    @Override
    public void streamCommits(RepoName repo,
                              LocalDateTime since,
                              LocalDateTime until,
                              Consumer<List<Commit>> batchHandler,
                              BooleanSupplier cancelled) {
        String url = findUrl(repo);
        try {
            GitCliClient.PreparedRepo prepared = cli.prepare(url, cancelled);

            // Аккумулятор батча. Парсер пушит сюда коммиты, мы flush'им по достижении BATCH_SIZE.
            final List<Commit> buffer = new ArrayList<>(BATCH_SIZE);
            final long[] totalRef = new long[]{0};

            GitLogParser.Streaming parser = new GitLogParser.Streaming(prepared.name(), commit -> {
                buffer.add(commit);
                if (buffer.size() >= BATCH_SIZE) {
                    batchHandler.accept(new ArrayList<>(buffer));
                    totalRef[0] += buffer.size();
                    buffer.clear();
                }
            });

            cli.streamLog(prepared.path(), since, until, parser::onLine, cancelled);
            parser.finish();

            if (!buffer.isEmpty()) {
                batchHandler.accept(new ArrayList<>(buffer));
                totalRef[0] += buffer.size();
            }

            log.info("Стримили {} коммитов из {} за период [{}..{}]",
                    totalRef[0], prepared.name().value(), since, until);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitOperationInterruptedException(repo, e);
        } catch (Exception e) {
            throw new GitOperationFailedException(repo, "не удалось собрать коммиты", e);
        }
    }

    /** Сопоставляет {@link RepoName} с одним из URL'ов в конфиге. */
    private String findUrl(RepoName repo) {
        return properties.repositories().stream()
                .filter(url -> RepoName.fromUrl(url).equals(repo))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "репозиторий " + repo + " не найден в конфигурации git.repositories"));
    }
}
