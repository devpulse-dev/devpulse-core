package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO запроса на синхронизацию Kaiten.
 * 
 * <p>Содержит параметры для синхронизации данных с системой Kaiten,
 * включая список email адресов команды и дату начала периода.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class KaitenSyncRequest {
    
    /**
     * Список email адресов команды.
     * 
     * <p>Список email адресов участников команды для синхронизации.</p>
     */
    private List<String> teamEmails;
    
    /**
     * Дата начала периода.
     * 
     * <p>Дата и время начала периода для синхронизации данных.</p>
     */
    private LocalDateTime since;
}
