package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;

/**
 * Вспомогательный класс для построения DTO задач с коммитами.
 * Отвечает за группировку коммитов по задачам и создание DTO.
 */
@Component
@Log4j2
public class TaskBuilder {

    /**
     * Строит список задач с коммитами из списка деталей коммитов.
     * Группирует коммиты по номеру задачи и сортирует по количеству коммитов.
     *
     * @param commits список деталей коммитов
     * @return список задач с коммитами, отсортированный по количеству коммитов
     */
    public List<TaskWithCommitsDto> buildTasksWithCommits(List<CommitDetails> commits) {
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

    /**
     * Строит DTO задачи с коммитами.
     *
     * @param taskNumber номер задачи
     * @param taskCommits список коммитов задачи
     * @return DTO задачи с коммитами
     */
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

    /**
     * Извлекает заголовок задачи из сообщения коммита.
     * Если сообщение начинается с номера задачи, возвращает оставшуюся часть.
     * Иначе возвращает всё сообщение.
     *
     * @param commitMessage сообщение коммита
     * @param taskNumber номер задачи
     * @return заголовок задачи
     */
    private String extractTaskTitle(String commitMessage, String taskNumber) {
        if (commitMessage == null || commitMessage.isBlank()) {
            return taskNumber;
        }

        // Оптимизировано: используем startsWith вместо regex
        String title = commitMessage.startsWith(taskNumber)
                ? commitMessage.substring(taskNumber.length()).trim()
                : commitMessage.trim();

        return title.isEmpty() ? taskNumber : title;
    }
}
