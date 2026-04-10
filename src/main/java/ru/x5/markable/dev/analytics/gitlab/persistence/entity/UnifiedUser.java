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
 * Сущность для хранения унифицированной информации о пользователях.
 * 
 * <p>Объединяет данные о пользователях из разных систем (GitLab, Kaiten)
 * в единую запись, используя email в качестве уникального идентификатора.
 * Позволяет связывать активности пользователя в разных системах.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "unified_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedUser {

    /**
     * Уникальный идентификатор пользователя в системе.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email пользователя (уникальный идентификатор).
     */
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * Имя пользователя (логин).
     */
    @Column(name = "username")
    private String username;

    /**
     * Отображаемое имя пользователя.
     */
    @Column(name = "name")
    private String name;

    /**
     * URL аватара пользователя.
     */
    @Column(name = "avatar_url")
    private String avatarUrl;

    /**
     * Идентификатор пользователя в системе Kaiten.
     */
    @Column(name = "kaiten_id")
    private Long kaitenId;

    /**
     * Идентификатор пользователя в системе GitLab.
     */
    @Column(name = "gitlab_id")
    private Integer gitlabId;

    /**
     * Дата и время последней синхронизации данных пользователя.
     */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    /**
     * Дата и время создания записи о пользователе.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления записи о пользователе.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
