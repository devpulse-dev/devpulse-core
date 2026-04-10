package ru.x5.markable.dev.analytics.commons.exceptions;

/**
 * Интерфейс для шаблонов сообщений API.
 * 
 * <p>Определяет контракт для создания форматированных сообщений об ошибках.
 * Позволяет использовать параметризованные сообщения с подстановкой аргументов.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface MessageTemplate {

    /**
     * Форматирует сообщение с подстановкой аргументов.
     * 
     * @param args аргументы, подставляемые в шаблон текста сообщения
     * @return текст сообщения с подставленными аргументами
     */
    String getText(Object... args);
}