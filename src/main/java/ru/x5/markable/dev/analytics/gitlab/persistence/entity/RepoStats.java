package ru.x5.markable.dev.analytics.gitlab.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Сущность для хранения статистики по репозиторию за период анализа.
 * 
 * <p>Содержит агрегированную статистику по конкретному репозиторию для конкретного запуска анализа,
 * включая количество коммитов, добавленных и удалённых строк кода.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "repo_stats",
        indexes = {
                @Index(name = "idx_repo_analysis", columnList = "analysis_id"),
                @Index(name = "idx_repo_name", columnList = "repository_name")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoStats {

    /**
     * Уникальный идентификатор записи статистики.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор запуска анализа.
     */
    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    /**
     * Название репозитория.
     */
    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    /**
     * Email автора.
     */
    @Column(nullable = false)
    private String email;

    /**
     * Количество merge-коммитов.
     */
    @Column(name = "merge_commits")
    long mergeCommits;

    /**
     * Общее количество коммитов.
     */
    @Column(nullable = false)
    private Long commits;

    /**
     * Количество добавленных строк кода.
     */
    @Column(name = "added_lines", nullable = false)
    private Long addedLines;

    /**
     * Количество удалённых строк кода.
     */
    @Column(name = "deleted_lines", nullable = false)
    private Long deletedLines;

    /**
     * Количество добавленных строк в тестовых файлах.
     */
    @Column(name = "test_added_lines")
    private Long testAddedLines;
}
