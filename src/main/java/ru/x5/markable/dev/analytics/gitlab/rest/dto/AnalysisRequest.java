package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * DTO для запроса анализа репозиториев.
 * 
 * <p>Содержит параметры для определения периода анализа репозиториев.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Data
public class AnalysisRequest {
    
    /**
     * Начальная дата периода анализа.
     * 
     * <p>Анализируются коммиты, сделанные начиная с этой даты (включительно).</p>
     */
    private LocalDate since;
    
    /**
     * Конечная дата периода анализа.
     * 
     * <p>Анализируются коммиты, сделанные до этой даты (включительно).</p>
     */
    private LocalDate until;
}
