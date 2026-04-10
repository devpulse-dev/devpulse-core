package ru.x5.markable.dev.analytics.gitlab.interactor;

import java.text.MessageFormat;
import lombok.AllArgsConstructor;
import ru.x5.markable.dev.analytics.commons.exceptions.MessageTemplate;

/**
 * Перечисление сообщений об ошибках для интерактора анализа.
 * 
 * <p>Содержит шаблоны сообщений об ошибках, которые могут возникнуть при анализе репозиториев.
 * Реализует интерфейс {@link MessageTemplate} для форматирования сообщений с аргументами.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@AllArgsConstructor
public enum Message implements MessageTemplate {
    /**
     * Ошибка при анализе репозитория.
     * 
     * <p>Аргументы: {0} - имя репозитория</p>
     */
    ANALYZE_REPOSITORY_ERROR("При обновлении репозитория {0} возникла ошибка"),
    
    /**
     * Ошибка выполнения Git-команды.
     */
    GIT_ERROR("Ошибка выполнения git команды"),
    
    /**
     * Ошибка сохранения статистики анализа.
     */
    STATISTICS_SAVE_ERROR("Ошибка сохранения статистики анализа");

    /**
     * Шаблон сообщения.
     */
    private final String template;

    /**
     * Форматирует сообщение с подстановкой аргументов.
     * 
     * @param args аргументы для подстановки в шаблон
     * @return форматированное сообщение
     */
    @Override
    public String getText(Object... args) {
        return String.format(template, args);
    }
}
