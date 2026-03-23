package ru.x5.markable.dev.analytics.gitlab.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;
import ru.x5.markable.dev.analytics.gitlab.persistence.repository.CommitDetailsRepository;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;
import ru.x5.markable.dev.analytics.gitlab.service.CommitDetailsService;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class CommitDetailsServiceImpl implements CommitDetailsService {

    private final CommitDetailsRepository commitDetailsRepository;

    @Override
    @Transactional
    public void saveCommitDetails(List<CommitDetail> commits) {
        log.info("Saving {} commit details", commits.size());

        List<CommitDetails> newCommits = new ArrayList<>();
        int skippedCount = 0;

        for (CommitDetail commit : commits) {
            if (commitDetailsRepository.existsByCommitHash(commit.getHash())) {
                skippedCount++;
                continue;
            }

            CommitDetails entity = CommitDetails.builder()
                    .commitHash(commit.getHash())
                    .email(commit.getEmail())
                    .commitDate(commit.getCommitDate())
                    .hour(commit.getCommitDate().getHour())
                    .isMerge(commit.isMerge())
                    .addedLines(commit.getAdded())
                    .deletedLines(commit.getDeleted())
                    .testAddedLines(commit.getTestAdded())
                    .repositoryName(commit.getRepoName())
                    .taskNumber(commit.getTaskNumber())
                    .commitMessage(commit.getCommitMessage())
                    .collectedAt(LocalDateTime.now())
                    .build();

            newCommits.add(entity);
        }

        if (!newCommits.isEmpty()) {
            commitDetailsRepository.saveAll(newCommits);
            log.info("Saved {} new commit details, skipped {} duplicates", newCommits.size(), skippedCount);
        }
    }

    @Override
    public List<CommitDetailDto> getUserCommits(String email) {
        List<CommitDetails> commits = commitDetailsRepository.findByEmailOrderByCommitDateAsc(email);

        return commits.stream()
                .map(CommitDetailDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Map<Integer, Long> getHourlyActivity(String email) {
        Map<Integer, Long> hourlyActivity = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            hourlyActivity.put(i, 0L);
        }

        List<Object[]> results = commitDetailsRepository.findHourlyActivityByEmail(email);
        for (Object[] result : results) {
            Integer hour = (Integer) result[0];
            Long count = (Long) result[1];
            if (hour != null) {
                hourlyActivity.put(hour, count);
            }
        }

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

        Map<String, List<CommitDetails>> commitsByTask = new LinkedHashMap<>();

        for (CommitDetails commit : commits) {
            String taskNumber = commit.getTaskNumber();
            if (taskNumber == null || taskNumber.isBlank()) {
                continue;
            }

            commitsByTask.computeIfAbsent(taskNumber, k -> new ArrayList<>()).add(commit);
        }

        List<TaskWithCommitsDto> result = new ArrayList<>();

        for (Map.Entry<String, List<CommitDetails>> entry : commitsByTask.entrySet()) {
            String taskNumber = entry.getKey();
            List<CommitDetails> taskCommits = entry.getValue();

            String taskTitle = extractTaskTitle(taskCommits.get(0).getCommitMessage(), taskNumber);

            List<CommitDetailDto> commitDtos = taskCommits.stream()
                    .map(CommitDetailDto::fromEntity)
                    .collect(Collectors.toList());

            result.add(TaskWithCommitsDto.builder()
                    .taskNumber(taskNumber)
                    .taskTitle(taskTitle)
                    .commits(commitDtos)
                    .build());
        }

        result.sort((a, b) -> Integer.compare(b.getCommits().size(), a.getCommits().size()));

        log.info("Found {} tasks with commits for user {}", result.size(), email);
        return result;
    }

    private String extractTaskTitle(String commitMessage, String taskNumber) {
        if (commitMessage == null || commitMessage.isBlank()) {
            return taskNumber;
        }

        String title = commitMessage.replaceFirst("^" + taskNumber, "").trim();

        if (title.isEmpty()) {
            return taskNumber;
        }

        return title;
    }

    @Override
    public Map<Integer, Long> getHourlyActivity(String email, LocalDate start, LocalDate end) {
        log.info("Fetching hourly activity for user: {} between {} and {}", email, start, end);

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<CommitDetails> commits = commitDetailsRepository
                .findByEmailAndCommitDateBetween(email, startDateTime, endDateTime);

        Map<Integer, Long> hourlyActivity = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            hourlyActivity.put(i, 0L);
        }

        for (CommitDetails commit : commits) {
            int hour = commit.getHour();
            hourlyActivity.merge(hour, 1L, Long::sum);
        }

        return hourlyActivity;
    }

    @Override
    public List<TaskWithCommitsDto> getTasksWithCommits(String email, LocalDate start, LocalDate end) {
        log.info("Fetching tasks with commits for user: {} between {} and {}", email, start, end);

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<CommitDetails> commits = commitDetailsRepository
                .findByEmailAndCommitDateBetween(email, startDateTime, endDateTime);

        Map<String, List<CommitDetails>> commitsByTask = new LinkedHashMap<>();

        for (CommitDetails commit : commits) {
            String taskNumber = commit.getTaskNumber();
            if (taskNumber == null || taskNumber.isBlank()) {
                continue;
            }
            commitsByTask.computeIfAbsent(taskNumber, k -> new ArrayList<>()).add(commit);
        }

        List<TaskWithCommitsDto> result = new ArrayList<>();

        for (Map.Entry<String, List<CommitDetails>> entry : commitsByTask.entrySet()) {
            String taskNumber = entry.getKey();
            List<CommitDetails> taskCommits = entry.getValue();

            String taskTitle = extractTaskTitle(taskCommits.get(0).getCommitMessage(), taskNumber);

            List<CommitDetailDto> commitDtos = taskCommits.stream()
                    .map(CommitDetailDto::fromEntity)
                    .collect(Collectors.toList());

            result.add(TaskWithCommitsDto.builder()
                    .taskNumber(taskNumber)
                    .taskTitle(taskTitle)
                    .commits(commitDtos)
                    .build());
        }

        result.sort((a, b) -> Integer.compare(b.getCommits().size(), a.getCommits().size()));

        return result;
    }
}
