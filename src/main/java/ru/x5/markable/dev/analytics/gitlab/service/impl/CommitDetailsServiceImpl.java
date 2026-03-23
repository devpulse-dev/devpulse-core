package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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

@Service
@Log4j2
@RequiredArgsConstructor
public class CommitDetailsServiceImpl implements CommitDetailsService {

    private final CommitDetailsRepository commitDetailsRepository;
    private final CommitDetailsMapper commitDetailsMapper;

    @Override
    @Transactional
    public void saveCommitDetails(List<CommitDetail> commits) {
        if (commits == null || commits.isEmpty()) {
            log.info("No commits to save");
            return;
        }

        log.info("Saving {} commit details", commits.size());

        // Batch check for existing commits to avoid N+1 problem
        List<String> commitHashes = commits.stream()
                .map(CommitDetail::getHash)
                .toList();

        Set<String> existingHashes = Set.copyOf(commitDetailsRepository.findExistingHashes(commitHashes));

        List<CommitDetails> newCommits = commits.stream()
                .filter(commit -> !existingHashes.contains(commit.getHash()))
                .map(commitDetailsMapper::toEntity)
                .toList();

        int skippedCount = commits.size() - newCommits.size();

        if (!newCommits.isEmpty()) {
            commitDetailsRepository.saveAll(newCommits);
            log.info("Saved {} new commit details, skipped {} duplicates", newCommits.size(), skippedCount);
        } else {
            log.info("All {} commits already exist in database", skippedCount);
        }
    }

    @Override
    public List<CommitDetailDto> getUserCommits(String email) {
        return commitDetailsRepository.findByEmailOrderByCommitDateAsc(email).stream()
                .map(CommitDetailDto::fromEntity)
                .toList();
    }

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

    @Override
    public boolean commitExists(String commitHash) {
        return commitDetailsRepository.existsByCommitHash(commitHash);
    }

    @Override
    public List<TaskWithCommitsDto> getTasksWithCommits(String email) {
        log.info("Fetching tasks with commits for user: {}", email);
        List<CommitDetails> commits = commitDetailsRepository.findByEmailOrderByCommitDateAsc(email);
        return buildTasksWithCommits(commits);
    }

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

    @Override
    public List<TaskWithCommitsDto> getTasksWithCommits(String email, LocalDate start, LocalDate end) {
        log.info("Fetching tasks with commits for user: {} between {} and {}", email, start, end);

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<CommitDetails> commits = commitDetailsRepository
                .findByEmailAndCommitDateBetween(email, startDateTime, endDateTime);

        return buildTasksWithCommits(commits);
    }

    private Map<Integer, Long> initializeHourlyMap() {
        return IntStream.range(0, 24)
                .boxed()
                .collect(Collectors.toMap(hour -> hour, hour -> 0L));
    }

    private List<TaskWithCommitsDto> buildTasksWithCommits(List<CommitDetails> commits) {
        Map<String, List<CommitDetails>> commitsByTask = commits.stream()
                .filter(commit -> commit.getTaskNumber() != null && !commit.getTaskNumber().isBlank())
                .filter(commit -> !commit.isMerge())
                .collect(Collectors.groupingBy(
                        CommitDetails::getTaskNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return commitsByTask.entrySet().stream()
                .map(entry -> buildTaskWithCommits(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Integer.compare(b.getCommits().size(), a.getCommits().size()))
                .toList();
    }

    private TaskWithCommitsDto buildTaskWithCommits(String taskNumber, List<CommitDetails> taskCommits) {
        String taskTitle = extractTaskTitle(taskCommits.get(0).getCommitMessage(), taskNumber);
        
        List<CommitDetailDto> commitDtos = taskCommits.stream()
                .map(CommitDetailDto::fromEntity)
                .toList();

        return TaskWithCommitsDto.builder()
                .taskNumber(taskNumber)
                .taskTitle(taskTitle)
                .commits(commitDtos)
                .build();
    }

    private String extractTaskTitle(String commitMessage, String taskNumber) {
        if (commitMessage == null || commitMessage.isBlank()) {
            return taskNumber;
        }

        // Optimized: use startsWith instead of regex
        String title = commitMessage.startsWith(taskNumber) 
                ? commitMessage.substring(taskNumber.length()).trim() 
                : commitMessage.trim();

        return title.isEmpty() ? taskNumber : title;
    }
}
