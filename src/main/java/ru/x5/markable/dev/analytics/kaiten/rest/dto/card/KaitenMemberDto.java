package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO участника (member) Kaiten.
 * 
 * <p>Содержит информацию об участнике в системе Kaiten, включая идентификатор,
 * UID, полное имя, email, имя пользователя, URL аватара и тип участия.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenMemberDto {
    
    /**
     * Идентификатор участника.
     * 
     * <p>Уникальный идентификатор участника в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * UID участника.
     * 
     * <p>Уникальный идентификатор участника в формате UID.</p>
     */
    private String uid;

    /**
     * Полное имя участника.
     * 
     * <p>Полное имя участника.</p>
     */
    @JsonProperty("full_name")
    private String fullName;

    /**
     * Email участника.
     * 
     * <p>Email адрес участника.</p>
     */
    private String email;
    
    /**
     * Имя пользователя.
     * 
     * <p>Имя пользователя (логин) участника.</p>
     */
    private String username;

    /**
     * URL аватара с инициалами.
     * 
     * <p>URL изображения аватара с инициалами участника.</p>
     */
    @JsonProperty("avatar_initials_url")
    private String avatarInitialsUrl;

    /**
     * Тип участия.
     * 
     * <p>Тип участия участника в карточке или проекте.</p>
     */
    private Integer type;
}