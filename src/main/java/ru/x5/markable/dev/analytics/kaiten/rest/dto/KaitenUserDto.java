package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO пользователя Kaiten.
 * 
 * <p>Содержит информацию о пользователе в системе Kaiten, включая идентификатор,
 * имя пользователя, email, полное имя и аватар.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenUserDto {
    
    /**
     * Идентификатор пользователя.
     * 
     * <p>Уникальный идентификатор пользователя в системе Kaiten.</p>
     */
    private Long id;
    
    /**
     * Имя пользователя.
     * 
     * <p>Имя пользователя в системе Kaiten.</p>
     */
    private String username;
    
    /**
     * Email пользователя.
     * 
     * <p>Email адрес пользователя.</p>
     */
    private String email;
    
    /**
     * Полное имя пользователя.
     * 
     * <p>Полное имя пользователя.</p>
     */
    @JsonProperty("full_name")
    private String fullName;
    
    /**
     * URL аватара.
     * 
     * <p>Ссылка на аватар пользователя.</p>
     */
    @JsonProperty("avatar_uploaded_url")
    private String avatar;
}
