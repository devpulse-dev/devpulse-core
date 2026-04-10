package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;

/**
 * DTO для деталей коммита.
 * 
 * <p>Содержит подробную информацию о коммите, включая хеш, автора, дату, 
 * количество добавленных и удалённых строк, а также связанную задачу.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitDetailDto {
    
    /**
     * Хеш коммита.
     * 
     * <p>Уникальный идентификатор коммита в системе контроля версий.</p>
     */
    private String hash;
    
    /**
     * Email автора.
     * 
     * <p>Email адрес автора коммита.</p>
     */
    private String email;
    
    /**
     * Дата коммита.
     * 
     * <p>Дата и время создания коммита.</p>
     */
    private LocalDateTime commitDate;
    
    /**
     * Является ли коммит слиянием.
     * 
     * <p>Флаг, указывающий, является ли коммит слиянием веток.</p>
     */
    private boolean isMerge;
    
    /**
     * Количество добавленных строк.
     * 
     * <p>Общее количество строк кода, добавленных в коммите.</p>
     */
    private long added;
    
    /**
     * Количество удалённых строк.
     * 
     * <p>Общее количество строк кода, удалённых в коммите.</p>
     */
    private long deleted;
    
    /**
     * Количество добавленных строк в тестах.
     * 
     * <p>Количество строк кода, добавленных в тестовых файлах.</p>
     */
    private long testAdded;
    
    /**
     * Название репозитория.
     * 
     * <p>Название репозитория, в котором был сделан коммит.</p>
     */
    private String repoName;
    
    /**
     * Номер задачи.
     * 
     * <p>Номер задачи, связанной с коммитом.</p>
     */
    private String taskNumber;
    
    /**
     * Сообщение коммита.
     * 
     * <p>Текст сообщения коммита.</p>
     */
    private String commitMessage;

    /**
     * Создаёт DTO из сущности.
     * 
     * <p>Преобразует сущность {@link CommitDetails} в DTO {@link CommitDetailDto}.</p>
     * 
     * @param entity сущность деталей коммита
     * @return DTO деталей коммита
     */
    public static CommitDetailDto fromEntity(CommitDetails entity) {
        return CommitDetailDto.builder()
                .hash(entity.getCommitHash())
                .email(entity.getEmail())
                .commitDate(entity.getCommitDate())
                .isMerge(entity.isMerge())
                .added(entity.getAddedLines())
                .deleted(entity.getDeletedLines())
                .testAdded(entity.getTestAddedLines())
                .repoName(entity.getRepositoryName())
                .taskNumber(entity.getTaskNumber())
                .commitMessage(entity.getCommitMessage())
                .build();
    }
}
