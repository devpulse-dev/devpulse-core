package ru.x5.markable.dev.analytics.gitlab.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Детальная информация о коммите.
 * 
 * <p>Содержит полную информацию о коммите: хеш, email автора, дата коммита,
 * является ли коммит merge-коммитом, количество добавленных и удаленных строк,
 * количество добавленных строк в тестовых файлах, название репозитория,
 * номер задачи и сообщение коммита.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitDetail {
    /**
     * Хеш коммита.
     */
    private String hash;
    
    /**
     * Email автора коммита.
     */
    private String email;
    
    /**
     * Дата и время коммита.
     */
    private LocalDateTime commitDate;
    
    /**
     * Является ли коммит merge-коммитом.
     */
    private boolean isMerge;
    
    /**
     * Количество добавленных строк.
     */
    private long added;
    
    /**
     * Количество удаленных строк.
     */
    private long deleted;
    
    /**
     * Количество добавленных строк в тестовых файлах.
     */
    private long testAdded;
    
    /**
     * Название репозитория.
     */
    private String repoName;
    
    /**
     * Номер задачи (из сообщения коммита).
     */
    private String taskNumber;
    
    /**
     * Сообщение коммита.
     */
    private String commitMessage;
}

