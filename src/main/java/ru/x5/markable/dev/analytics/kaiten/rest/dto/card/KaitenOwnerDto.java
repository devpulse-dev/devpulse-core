package ru.x5.markable.dev.analytics.kaiten.rest.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DTO владельца (owner) Kaiten.
 * 
 * <p>Содержит подробную информацию о владельце карточки в системе Kaiten, включая
 * идентификатор, UID, полное имя, email, имя пользователя, URL аватара, инициалы,
 * тип аватара, язык, часовой пояс, тему, даты создания и обновления, статус активации,
 * версию UI и признак виртуального пользователя.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenOwnerDto {
    
    /**
     * Идентификатор владельца.
     * 
     * <p>Уникальный идентификатор владельца в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * UID владельца.
     * 
     * <p>Уникальный идентификатор владельца в формате UID.</p>
     */
    private String uid;

    /**
     * Полное имя владельца.
     * 
     * <p>Полное имя владельца.</p>
     */
    @JsonProperty("full_name")
    private String fullName;

    /**
     * Email владельца.
     * 
     * <p>Email адрес владельца.</p>
     */
    private String email;
    
    /**
     * Имя пользователя.
     * 
     * <p>Имя пользователя (логин) владельца.</p>
     */
    private String username;

    /**
     * URL аватара с инициалами.
     * 
     * <p>URL изображения аватара с инициалами владельца.</p>
     */
    @JsonProperty("avatar_initials_url")
    private String avatarInitialsUrl;

    /**
     * URL загруженного аватара.
     * 
     * <p>URL изображения загруженного аватара владельца.</p>
     */
    @JsonProperty("avatar_uploaded_url")
    private String avatarUploadedUrl;

    /**
     * Инициалы.
     * 
     * <p>Инициалы владельца.</p>
     */
    private String initials;

    /**
     * Тип аватара.
     * 
     * <p>Тип аватара владельца.</p>
     */
    @JsonProperty("avatar_type")
    private Integer avatarType;

    /**
     * Язык.
     * 
     * <p>Код языка интерфейса пользователя.</p>
     */
    private String lng;
    
    /**
     * Часовой пояс.
     * 
     * <p>Часовой пояс пользователя.</p>
     */
    private String timezone;
    
    /**
     * Тема.
     * 
     * <p>Тема интерфейса пользователя.</p>
     */
    private String theme;
    
    /**
     * Дата создания.
     * 
     * <p>Дата и время создания пользователя.</p>
     */
    private LocalDateTime created;
    
    /**
     * Дата обновления.
     * 
     * <p>Дата и время последнего обновления пользователя.</p>
     */
    private LocalDateTime updated;
    
    /**
     * Статус активации.
     * 
     * <p>Признак активации пользователя.</p>
     */
    private Boolean activated;

    /**
     * Версия UI.
     * 
     * <p>Версия пользовательского интерфейса.</p>
     */
    @JsonProperty("ui_version")
    private Integer uiVersion;

    /**
     * Виртуальный пользователь.
     * 
     * <p>Признак виртуального пользователя.</p>
     */
    private Boolean virtual;
}