package ru.x5.markable.dev.analytics.gitlab.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Сущность для хранения ежедневной статистики автора.
 * 
 * <p>Содержит статистику активности автора за конкретный день в конкретном репозитории,
 * включая количество коммитов, добавленных и удалённых строк кода.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "daily_author_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"email", "date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAuthorStats {

    /**
     * Уникальный идентификатор записи статистики.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email автора.
     */
    @Column(name = "email", nullable = false)
    private String email;

    /**
     * Дата статистики.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Количество merge-коммитов.
     */
    @Column(name = "merge_commits")
    private Long mergeCommits;

    /**
     * Общее количество коммитов.
     */
    @Column(name = "commits")
    private Long commits;

    /**
     * Количество добавленных строк кода.
     */
    @Column(name = "added_lines")
    private Long addedLines;

    /**
     * Количество удалённых строк кода.
     */
    @Column(name = "deleted_lines")
    private Long deletedLines;

    /**
     * Количество добавленных строк в тестовых файлах.
     */
    @Column(name = "test_added_lines")
    private Long testAddedLines;

    /**
     * Дата и время последнего обновления записи.
     */
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /**
     * Название репозитория.
     */
    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    /**
     * Идентификатор пользователя (если связан с UnifiedUser).
     */
    @Column(name = "user_id")
    private Long userId;
}
