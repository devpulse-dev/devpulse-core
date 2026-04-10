package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для унифицированного пользователя.
 * 
 * <p>Содержит информацию о пользователе, объединённую из разных систем (GitLab, Kaiten).</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
public class UnifiedUserDto {
    
    /**
     * Идентификатор пользователя.
     * 
     * <p>Уникальный идентификатор пользователя в системе.</p>
     */
    private Long id;
    
    /**
     * Email пользователя.
     * 
     * <p>Электронная почта пользователя.</p>
     */
    private String email;
    
    /**
     * Имя пользователя.
     * 
     * <p>Имя пользователя (username).</p>
     */
    private String username;
    
    /**
     * Полное имя пользователя.
     * 
     * <p>Полное имя пользователя.</p>
     */
    private String name;
    
    /**
     * URL аватара.
     * 
     * <p>Ссылка на аватар пользователя.</p>
     */
    private String avatarUrl;
    
    /**
     * Идентификатор в Kaiten.
     * 
     * <p>Идентификатор пользователя в системе Kaiten.</p>
     */
    private Long kaitenId;
    
    /**
     * Идентификатор в GitLab.
     * 
     * <p>Идентификатор пользователя в системе GitLab.</p>
     */
    private Integer gitlabId;
}
