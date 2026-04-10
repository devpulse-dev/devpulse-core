package ru.x5.markable.dev.analytics.gitlab.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Сущность для хранения детальной информации о коммитах.
 * 
 * <p>Содержит полную информацию о каждом коммите, включая хеш, автора,
 * дату, сообщение, количество добавленных и удалённых строк, а также связь
 * с задачей и карточкой Kaiten.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "commit_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitDetails {

    /**
     * Уникальный идентификатор записи о коммите.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Хеш коммита (уникальный идентификатор в Git).
     */
    @Column(name = "commit_hash", nullable = false, unique = true)
    private String commitHash;

    /**
     * Email автора коммита.
     */
    @Column(name = "email", nullable = false)
    private String email;

    /**
     * Дата и время создания коммита.
     */
    @Column(name = "commit_date", nullable = false)
    private LocalDateTime commitDate;

    /**
     * Час дня, когда был сделан коммит (0-23).
     */
    @Column(name = "hour")
    private Integer hour;

    /**
     * Признак того, что коммит является merge-коммитом.
     */
    @Column(name = "is_merge")
    private boolean isMerge;

    /**
     * Номер задачи, связанной с коммитом.
     */
    @Column(name = "task_number")
    private String taskNumber;

    /**
     * Сообщение коммита.
     */
    @Column(name = "commit_message")
    private String commitMessage;

    /**
     * Количество добавленных строк кода.
     */
    @Column(name = "added_lines")
    private long addedLines;

    /**
     * Количество удалённых строк кода.
     */
    @Column(name = "deleted_lines")
    private long deletedLines;

    /**
     * Количество добавленных строк в тестовых файлах.
     */
    @Column(name = "test_added_lines")
    private long testAddedLines;

    /**
     * Название репозитория, в котором был сделан коммит.
     */
    @Column(name = "repository_name")
    private String repositoryName;

    /**
     * Дата и время сбора информации о коммите.
     */
    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    /**
     * Идентификатор унифицированного пользователя.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Идентификатор карточки в Kaiten, связанной с коммитом.
     */
    @Column(name = "kaiten_card_id")
    private Long kaitenCardId;
}
