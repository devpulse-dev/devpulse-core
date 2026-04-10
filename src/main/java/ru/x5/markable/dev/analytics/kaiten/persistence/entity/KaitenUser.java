package ru.x5.markable.dev.analytics.kaiten.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Сущность для хранения пользователей из системы Kaiten.
 * 
 * <p>Содержит информацию о пользователе, включая имя пользователя,
 * email, отображаемое имя, URL аватара и время последней синхронизации.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "kaiten_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KaitenUser {

    /**
     * Уникальный идентификатор пользователя в Kaiten.
     */
    @Id
    private Long id;

    /**
     * Имя пользователя (логин).
     */
    @Column(name = "username", nullable = false)
    private String username;

    /**
     * Email пользователя.
     */
    @Column(name = "email", nullable = false)
    private String email;

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
     * Дата и время последней синхронизации данных пользователя.
     */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
