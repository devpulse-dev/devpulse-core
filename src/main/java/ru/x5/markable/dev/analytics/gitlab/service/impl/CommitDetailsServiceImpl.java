package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.x5.markable.dev.analytics.gitlab.mapper.CommitDetailsMapper;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.CommitDetailsRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;
import ru.x5.markable.dev.analytics.gitlab.service.CommitDetailsService;
import ru.x5.markable.dev.analytics.gitlab.service.UnifiedUserService;
import ru.x5.markable.dev.analytics.gitlab.service.impl.helper.TaskBuilder;

/**
 * Сервис для работы с деталями коммитов.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Сохранение деталей коммитов в базу данных</li>
 *   <li>Получение коммитов пользователя</li>
 *   <li>Расчёт почасовой активности</li>
 *   <li>Группировка коммитов по задачам</li>
 * </ul>
 * 
 * <p>Сервис использует вспомогательные классы для разделения ответственности:</p>
 * <ul>
 *   <li>{@link TaskBuilder} - построение DTO задач с коммитами</li>
 * </ul>
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class CommitDetailsServiceImpl implements CommitDetailsService {

    private final CommitDetailsRepository commitDetailsRepository;
    private final CommitDetailsMapper commitDetailsMapper;
    private final TaskBuilder taskBuilder;
    private final UnifiedUserService unifiedUserService;

    /**
     * Сохраняет детали коммитов в базу данных.
     * Пропускает дубликаты и использует пакетное сохранение для оптимизации.
     *
     * @param commits список деталей коммитов
     */
    @Override
    @Transactional
    public void saveCommitDetails(List<CommitDetail> commits) {
        if (commits == null || commits.isEmpty()) {
            log.info("No commits to save");
            return;
        }

        log.info("Saving {} commit details", commits.size());

        List<String> commitHashes = commits.stream()
                .map(CommitDetail::getHash)
                .toList();

        Set<String> existingHashes = Set.copyOf(commitDetailsRepository.findExistingHashes(commitHashes));

        // Один batch find-or-create вместо N findOrCreateByEmail
        Set<String> uniqueEmails = new HashSet<>();
        for (CommitDetail c : commits) {
            if (!existingHashes.contains(c.getHash()) && c.getEmail() != null) {
                uniqueEmails.add(c.getEmail().toLowerCase());
            }
        }
        Map<String, Long> userIdByEmail = unifiedUserService.findOrCreateAllByEmails(uniqueEmails);

        List<CommitDetails> newCommits = new ArrayList<>();

        for (CommitDetail commit : commits) {
            if (existingHashes.contains(commit.getHash())) {
                continue;
            }

            Long userId = commit.getEmail() == null
                    ? null
                    : userIdByEmail.get(commit.getEmail().toLowerCase());

            CommitDetails entity = commitDetailsMapper.toEntity(commit);
            entity.setUserId(userId);

            if (commit.getTaskNumber() != null) {
                try {
                    Long kaitenCardId = Long.parseLong(commit.getTaskNumber());
                    entity.setKaitenCardId(kaitenCardId);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            newCommits.add(entity);
        }

        int skippedCount = commits.size() - newCommits.size();

        if (!newCommits.isEmpty()) {
            // Сохраняем пачками по 500 для уменьшения нагрузки
            batchSave(newCommits);
            log.info("Saved {} new commit details, skipped {} duplicates", newCommits.size(), skippedCount);
        } else {
            log.info("All {} commits already exist in database", skippedCount);
        }
    }

    /**
     * Получает коммиты пользователя.
     *
     * @param email email пользователя
     * @return список деталей коммитов
     */
    @Override
    public List<CommitDetailDto> getUserCommits(String email) {
        return commitDetailsRepository.findByEmailOrderByCommitDateAsc(email).stream()
                .map(CommitDetailDto::fromEntity)
                .toList();
    }

    /**
     * Получает почасовую активность пользователя.
     *
     * @param email email пользователя
     * @return карта активности по часам (0-23)
     */
    @Override
    public Map<Integer, Long> getHourlyActivity(String email) {
        Map<Integer, Long> hourlyActivity = initializeHourlyMap();

        commitDetailsRepository.findHourlyActivityByEmail(email).forEach(result -> {
            Integer hour = (Integer) result[0];
            Long count = (Long) result[1];
            if (hour != null) {
                hourlyActivity.put(hour, count);
            }
        });

        return hourlyActivity;
    }

    /**
     * Проверяет существование коммита по хешу.
     *
     * @param commitHash хеш коммита
     * @return true если коммит существует
     */
    @Override
    public boolean commitExists(String commitHash) {
        return commitDetailsRepository.existsByCommitHash(commitHash);
    }

    /**
     * Получает задачи с коммитами пользователя.
     *
     * @param email email пользователя
     * @return список задач с коммитами
     */
    @Override
    public List<TaskWithCommitsDto> getTasksWithCommits(String email) {
        log.info("Fetching tasks with commits for user: {}", email);
        List<CommitDetails> commits = commitDetailsRepository.findByEmailOrderByCommitDateAsc(email);
        return taskBuilder.buildTasksWithCommits(commits);
    }

    /**
     * Получает почасовую активность пользователя за период.
     *
     * @param email email пользователя
     * @param start начало периода
     * @param end конец периода
     * @return карта активности по часам (0-23)
     */
    @Override
    public Map<Integer, Long> getHourlyActivity(String email, LocalDate start, LocalDate end) {
        log.info("Fetching hourly activity for user: {} between {} and {}", email, start, end);

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<CommitDetails> commits = commitDetailsRepository
                .findByEmailAndCommitDateBetween(email, startDateTime, endDateTime)
                .stream().filter(p -> !p.isMerge())
                .toList();

        Map<Integer, Long> hourlyActivity = initializeHourlyMap();

        commits.forEach(commit ->
                hourlyActivity.merge(commit.getHour(), 1L, Long::sum)
        );

        return hourlyActivity;
    }

    /**
     * Получает задачи с коммитами пользователя за период.
     *
     * @param email email пользователя
     * @param start начало периода
     * @param end конец периода
     * @return список задач с коммитами
     */
    @Override
    public List<TaskWithCommitsDto> getTasksWithCommits(String email, LocalDate start, LocalDate end) {
        log.info("Fetching tasks with commits for user: {} between {} and {}", email, start, end);

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<CommitDetails> commits = commitDetailsRepository
                .findByEmailAndCommitDateBetween(email, startDateTime, endDateTime);

        return taskBuilder.buildTasksWithCommits(commits);
    }

    /**
     * Получает коммиты пользователя за период.
     *
     * @param email email пользователя
     * @param start начало периода
     * @param end конец периода
     * @return список деталей коммитов
     */
    @Override
    public List<CommitDetailDto> getUserCommits(String email, LocalDate start, LocalDate end) {
        log.info("Fetching commits for user: {} between {} and {}", email, start, end);

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        return commitDetailsRepository
                .findByEmailAndCommitDateBetween(email, startDateTime, endDateTime)
                .stream()
                .map(CommitDetailDto::fromEntity)
                .toList();
    }

    /**
     * Инициализирует карту почасовой активности нулевыми значениями.
     *
     * @return карта с 24 часами (0-23)
     */
    private Map<Integer, Long> initializeHourlyMap() {
        return IntStream.range(0, 24)
                .boxed()
                .collect(Collectors.toMap(hour -> hour, hour -> 0L));
    }

    /**
     * Сохраняет коммиты пачками для оптимизации производительности.
     *
     * @param commits список коммитов для сохранения
     */
    private void batchSave(List<CommitDetails> commits) {
        int batchSize = 500;
        for (int i = 0; i < commits.size(); i += batchSize) {
            int end = Math.min(i + batchSize, commits.size());
            List<CommitDetails> batch = commits.subList(i, end);
            commitDetailsRepository.saveAll(batch);
            log.debug("Saved batch {} of {}", i / batchSize + 1, (commits.size() + batchSize - 1) / batchSize);
        }
    }
}
