package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.gitlab.config.GitProperties;
import ru.x5.markable.dev.analytics.gitlab.exception.RepositoryAnalysisException;
import ru.x5.markable.dev.analytics.gitlab.exception.StatisticsPersistenceException;
import ru.x5.markable.dev.analytics.gitlab.client.GitClient;
import ru.x5.markable.dev.analytics.gitlab.model.AuthorAggregate;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AnalysisRun;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AnalysisStatus;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.AuthorStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.RepoStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.AnalysisRunRepository;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.AuthorStatsRepository;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.RepoStatsRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AnalysisRequest;
import ru.x5.markable.dev.analytics.gitlab.service.AnalysisService;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Сервис для анализа Git репозиториев и сбора статистики коммитов.
 * 
 * <p>Обеспечивает асинхронный анализ нескольких репозиториев, сбор статистики по авторам,
 * сохранение результатов в базу данных и обработку ошибок.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Анализ коммитов в указанном временном периоде</li>
 *   <li>Сбор статистики по авторам (количество коммитов, добавленных/удаленных строк)</li>
 *   <li>Выделение тестовых файлов из общей статистики</li>
 *   <li>Асинхронная обработка нескольких репозиториев</li>
 *   <li>Сохранение результатов анализа в базу данных</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see AnalysisService
 * @see AnalysisRequest
 * @see AuthorStats
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private final GitClient gitClient;
    private final AnalysisRunRepository analysisRunRepository;
    private final AuthorStatsRepository authorStatsRepository;
    private final RepoStatsRepository repoStatsRepository;
    private final GitProperties gitProperties;
    private final Executor analysisExecutor;

    /**
     * Запускает анализ репозиториев в указанном временном периоде.
     * 
     * <p>Создает запись о запуске анализа, выполняет асинхронную обработку репозиториев
     * и возвращает агрегированную статистику по авторам.</p>
     * 
     * @param request запрос на анализ с указанием периода
     * @return список статистики по авторам
     */
    @Override
    @Transactional
    public List<AuthorStats> startAnalysis(AnalysisRequest request) {

        log.info("Starting analysis. Period: {} - {}", request.getSince(), request.getUntil());

        AnalysisRun run = AnalysisRun.builder()
                .startedAt(LocalDateTime.now())
                .sinceDate(request.getSince())
                .untilDate(request.getUntil())
                .status(AnalysisStatus.RUNNING)
                .build();

        analysisRunRepository.save(run);

       return executeAsync(run.getId(), request);
    }

    /**
     * Выполняет асинхронный анализ репозиториев.
     * 
     * <p>Обрабатывает все репозитории параллельно, агрегирует статистику по авторам,
     * сохраняет результаты в базу данных и отмечает статус анализа.</p>
     * 
     * @param analysisId идентификатор запуска анализа
     * @param request запрос на анализ
     * @return список агрегированной статистики по авторам, или null в случае ошибки
     */
    private List<AuthorStats> executeAsync(UUID analysisId, AnalysisRequest request) {

        long totalStart = System.currentTimeMillis();

        try {

            Map<String, AuthorAggregate> globalStats =
                    new ConcurrentHashMap<>();

            log.info("Processing {} repositories", gitProperties.getRepositories().size());

            List<CompletableFuture<Void>> futures = gitProperties.getRepositories()
                    .stream()
                    .map(repo -> CompletableFuture.runAsync(
                            () -> processRepositorySafely(repo, request, analysisId, globalStats),
                            analysisExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<AuthorStats> aggregatedStats = saveAggregatedStats(analysisId, globalStats);
            markSuccess(analysisId);

            log.info("Analysis {} completed in {} ms",
                    analysisId,
                    System.currentTimeMillis() - totalStart);

            return aggregatedStats;

        } catch (Exception e) {
            markFailed(analysisId, e.getMessage());
            log.error("Analysis {} failed", analysisId, e);
        }
        return Collections.emptyList();
    }

    /**
     * Безопасно обрабатывает репозиторий с обработкой исключений.
     * 
     * @param repo URL репозитория
     * @param request запрос на анализ
     * @param analysisId идентификатор запуска анализа
     * @param globalStats глобальная статистика для агрегации
     * @throws RepositoryAnalysisException при ошибке обработки репозитория
     */
    private void processRepositorySafely(String repo,
            AnalysisRequest request,
            UUID analysisId,
            Map<String, AuthorAggregate> globalStats) {

        try {
            processRepository(repo, request, analysisId, globalStats);
        } catch (IOException e) {
            throw new RepositoryAnalysisException(repo, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RepositoryAnalysisException(repo, e);
        }
    }

    /**
     * Обрабатывает отдельный репозиторий.
     * 
     * <p>Подготавливает репозиторий, собирает статистику коммитов через Git,
     * парсит вывод Git, сохраняет статистику репозитория и агрегирует в глобальную статистику.</p>
     * 
     * @param repoUrl URL репозитория
     * @param request запрос на анализ
     * @param analysisId идентификатор запуска анализа
     * @param globalStats глобальная статистика для агрегации
     * @throws IOException при ошибке работы с файловой системой или Git
     * @throws InterruptedException при прерывании потока
     */
    private void processRepository(String repoUrl,
            AnalysisRequest request,
            UUID analysisId,
            Map<String, AuthorAggregate> globalStats) throws IOException, InterruptedException {

        String repoName = extractRepoName(repoUrl);

        long start = System.currentTimeMillis();

        log.info("Processing repository [{}]", repoName);

        Path repoPath = gitClient.prepareRepository(repoUrl);

        List<String> lines =
                gitClient.collectStats(repoPath,
                        request.getSince(),
                        request.getUntil());

        log.info("Git returned {} lines for repo {}", lines.size(), repoName);

        Map<String, AuthorAggregate> repoStats = parseGitOutput(lines);

        saveRepoStats(repoName, analysisId, repoStats);

        repoStats.forEach((email, stat) ->
                globalStats.merge(email, stat, AuthorAggregate::merge));

        log.info("Repository [{}] processed in {} ms",
                repoName,
                System.currentTimeMillis() - start);
    }

    /**
     * Парсит вывод Git команды для извлечения статистики коммитов.
     * 
     * <p>Обрабатывает строки вывода Git, выделяет информацию о коммитах и изменениях файлов.
     * Определяет тестовые файлы и исключает бинарные файлы из статистики.</p>
     * 
     * @param lines строки вывода Git команды
     * @return карта статистики по авторам
     */
    private Map<String, AuthorAggregate> parseGitOutput(List<String> lines) {

        Map<String, AuthorAggregate> repoStats = new HashMap<>();
        String currentEmail = null;
        boolean currentCommitIsMerge;

        for (String rawLine : lines) {

            if (rawLine == null) continue;

            String line = rawLine.trim();

            if (line.isEmpty()) continue;

            // EMAIL = новый commit
            if (!line.contains("\t") && line.contains("@")) {

                String[] parts = line.split("\\|");

                currentEmail = parts[0].trim();
                currentCommitIsMerge =
                        parts.length > 1 &&
                                parts[1] != null &&
                                parts[1].trim().contains(" ");

                boolean finalCurrentCommitIsMerge = currentCommitIsMerge;

                repoStats.compute(currentEmail, (email, aggregate) -> {

                    if (aggregate == null) {
                        aggregate = new AuthorAggregate(email);
                    }

                    return aggregate.addCommit(finalCurrentCommitIsMerge);
                });

                continue;
            }

            // NUMSTAT
            if (currentEmail != null && line.contains("\t")) {

                String[] parts = line.split("\t");

                if (parts.length < 3) {
                    continue;
                }

                // бинарные файлы отображаются как "-"
                if (parts[0].equals("-") || parts[1].equals("-")) {
                    continue;
                }

                long added;
                long deleted;

                try {
                    added = Long.parseLong(parts[0]);
                    deleted = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }

                String fileName = parts[2];

                boolean isTestFile = isTestFile(fileName);

                repoStats.computeIfPresent(currentEmail, (email, aggregate) ->
                        aggregate.addLines(added, deleted, isTestFile)
                );
            }
        }

        return repoStats;
    }

    /**
     * Проверяет, является ли файл тестовым.
     * 
     * <p>Файл считается тестовым, если:</p>
     * <ul>
     *   <li>Находится в директории /test/</li>
     *   <li>Имеет суффикс test.java</li>
     *   <li>Имеет суффикс tests.java</li>
     * </ul>
     * 
     * @param fileName имя файла
     * @return true если файл является тестовым, false в противном случае
     */
    private boolean isTestFile(String fileName) {

        if (fileName == null) {
            return false;
        }

        String lower = fileName.toLowerCase();

        return lower.contains("/test/")
                || lower.endsWith("test.java")
                || lower.endsWith("tests.java");
    }

    /**
     * Сохраняет статистику репозитория в базу данных.
     * 
     * @param repoName имя репозитория
     * @param analysisId идентификатор запуска анализа
     * @param repoStats статистика репозитория
     * @throws StatisticsPersistenceException при ошибке сохранения в базу данных
     */
    private void saveRepoStats(String repoName,
            UUID analysisId,
            Map<String, AuthorAggregate> repoStats) {
        try {
            List<RepoStats> entities =
                    repoStats.values().stream()
                            .map(stat -> RepoStats.builder()
                                    .analysisId(analysisId)
                                    .repositoryName(repoName)
                                    .email(stat.email())
                                    .mergeCommits(stat.mergeCommits())
                                    .commits(stat.commits())
                                    .addedLines(stat.added())
                                    .deletedLines(stat.deleted())
                                    .testAddedLines(stat.testAdded())
                                    .build())
                            .toList();

            repoStatsRepository.saveAll(entities);
        } catch (Exception e) {
            throw new StatisticsPersistenceException(e);
        }
    }

    /**
     * Сохраняет агрегированную статистику по авторам в базу данных.
     * 
     * @param analysisId идентификатор запуска анализа
     * @param globalStats глобальная агрегированная статистика
     * @return список сохраненной статистики по авторам
     */
    private List<AuthorStats> saveAggregatedStats(UUID analysisId,
            Map<String, AuthorAggregate> globalStats) {

        List<AuthorStats> entities =
                globalStats.values().stream()
                        .map(stat -> AuthorStats.builder()
                                .analysisId(analysisId)
                                .email(stat.email())
                                .mergeCommits(stat.mergeCommits())
                                .commits(stat.commits())
                                .addedLines(stat.added())
                                .deletedLines(stat.deleted())
                                .testAddedLines(stat.testAdded())
                                .build())
                        .toList();

        authorStatsRepository.saveAll(entities);
        return entities;
    }

    /**
     * Отмечает анализ как успешно завершенный.
     * 
     * @param id идентификатор запуска анализа
     */
    private void markSuccess(UUID id) {
        AnalysisRun run = analysisRunRepository.findById(id).orElseThrow();
        run.setStatus(AnalysisStatus.SUCCESS);
        run.setFinishedAt(LocalDateTime.now());
        analysisRunRepository.save(run);
    }

    /**
     * Отмечает анализ как завершенный с ошибкой.
     * 
     * @param id идентификатор запуска анализа
     * @param error сообщение об ошибке
     */
    private void markFailed(UUID id, String error) {
        AnalysisRun run = analysisRunRepository.findById(id).orElseThrow();
        run.setStatus(AnalysisStatus.FAILED);
        run.setErrorMessage(error);
        run.setFinishedAt(LocalDateTime.now());
        analysisRunRepository.save(run);
    }

    /**
     * Извлекает имя репозитория из URL.
     * 
     * @param repoUrl URL репозитория
     * @return имя репозитория без расширения .git
     */
    private String extractRepoName(String repoUrl) {
        return repoUrl.substring(repoUrl.lastIndexOf("/") + 1)
                .replace(".git", "");
    }
}