package ru.x5.markable.dev.analytics.kaiten.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Сущность для хранения участников карточек Kaiten.
 * 
 * <p>Содержит информацию о пользователях, добавленных к карточке,
 * включая тип участия (участник, создатель и т.д.) и дату добавления.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Entity
@Table(name = "kaiten_card_member")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KaitenCardMember {

    /**
     * Уникальный идентификатор записи участника.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор карточки.
     */
    @Column(name = "card_id", nullable = false)
    private Long cardId;

    /**
     * Идентификатор пользователя.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Имя пользователя.
     */
    @Column(name = "user_name")
    private String userName;

    /**
     * Email пользователя.
     */
    @Column(name = "user_email")
    private String userEmail;

    /**
     * Тип участия (1 - участник, 2 - создатель и т.д.).
     */
    @Column(name = "member_type")
    private Integer memberType;

    /**
     * Дата и время добавления пользователя к карточке.
     */
    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}