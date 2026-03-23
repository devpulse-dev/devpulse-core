package ru.x5.markable.dev.analytics.gitlab.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;

public interface CommitDetailsService {

    void saveCommitDetails(List<CommitDetail> commits);

    List<CommitDetailDto> getUserCommits(String email);

    Map<Integer, Long> getHourlyActivity(String email);

    boolean commitExists(String commitHash);

    List<TaskWithCommitsDto> getTasksWithCommits(String email);

    Map<Integer, Long> getHourlyActivity(String email, LocalDate start, LocalDate end);

    List<TaskWithCommitsDto> getTasksWithCommits(String email, LocalDate start, LocalDate end);

}
