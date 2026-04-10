package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.gitlab.model.AuthorAggregate;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.UnifiedUser;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.DailyAuthorStatsRepository;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;

import java.time.LocalDate;

/**
 * Вспомогательный класс для сохранения статистики в базу данных.
 * Отвечает за создание и обновление записей статистики.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class StatsPersistenceHelper {

    private final DailyAuthorStatsRepository dailyStatsRepository;
    private final UnifiedUserService unifiedUserService;

    /**
     * Сохраняет статистику по дням для конкретного репозитория.
     * Обновляет существующие записи или создаёт новые.
     *
     * @param dailyStats карта сгруппированной статистики по дням и авторам
     * @param repoName имя репозитория
     */
    @Transactional
    public void saveDailyStatsForRepo(Map<LocalDate, Map<String, AuthorAggregate>> dailyStats, String repoName) {
        List<DailyAuthorStats> newRecords = new ArrayList<>();
        List<DailyAuthorStats> updateRecords = new ArrayList<>();

        // Кэш для уже найденных пользователей, чтобы не ходить в БД каждый раз
        Map<String, Long> userCache = new HashMap<>();

        for (Map.Entry<LocalDate, Map<String, AuthorAggregate>> dayEntry : dailyStats.entrySet()) {
            LocalDate date = dayEntry.getKey();
            Map<String, AuthorAggregate> dayStats = dayEntry.getValue();

            for (Map.Entry<String, AuthorAggregate> statEntry : dayStats.entrySet()) {
                String email = statEntry.getKey();
                AuthorAggregate stat = statEntry.getValue();

                Long userId = userCache.computeIfAbsent(email, e -> {
                    UnifiedUser user = unifiedUserService.findOrCreateByEmail(e);
                    return user.getId();
                });

                dailyStatsRepository.findByEmailAndDateAndRepositoryName(email, date, repoName)
                        .ifPresentOrElse(existing -> {
                            existing.setMergeCommits(stat.mergeCommits());
                            existing.setCommits(stat.commits());
                            existing.setAddedLines(stat.added());
                            existing.setDeletedLines(stat.deleted());
                            existing.setTestAddedLines(stat.testAdded());
                            existing.setLastUpdated(LocalDateTime.now());
                            existing.setUserId(userId);
                            updateRecords.add(existing);
                        }, () -> {
                            DailyAuthorStats newStats = DailyAuthorStats.builder()
                                    .email(email)
                                    .date(date)
                                    .repositoryName(repoName)
                                    .mergeCommits(stat.mergeCommits())
                                    .commits(stat.commits())
                                    .addedLines(stat.added())
                                    .deletedLines(stat.deleted())
                                    .testAddedLines(stat.testAdded())
                                    .lastUpdated(LocalDateTime.now())
                                    .userId(userId)
                                    .build();
                            newRecords.add(newStats);
                        });
            }
        }

        if (CollectionUtils.isNotEmpty(newRecords)) {
            dailyStatsRepository.saveAll(newRecords);
            log.info("Saved {} new daily stats records for repo {}", newRecords.size(), repoName);
        }

        if (CollectionUtils.isNotEmpty(updateRecords)) {
            dailyStatsRepository.saveAll(updateRecords);
            log.info("Updated {} existing daily stats records for repo {}", updateRecords.size(), repoName);
        }
    }
}
