package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyUserStatsDto {
    private LocalDate date;
    private String email;
    private long commits;
    private long mergeCommits;
    private long addedLines;
    private long deletedLines;
    private long testAddedLines;
}
