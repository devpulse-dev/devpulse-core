package ru.x5.markable.dev.analytics.kaiten.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для сбора карточек из системы Kaiten.
 * 
 * <p>Предоставляет функциональность для сбора карточек из разных пространств,
 * для команд и всех пользователей. Позволяет собирать карточки, изменённые
 * после указанного момента времени.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface KaitenCardCollectorService {
    
    /**
     * Собрать карточки из всех пространств.
     * 
     * <p>Собирает все карточки из всех доступных пространств в Kaiten,
     * которые были изменены после указанного момента времени.</p>
     * 
     * @param since момент времени, с которого собирать изменения
     */
    void collectCardsFromAllSpaces(LocalDateTime since);
    
    /**
     * Собрать карточки для команды.
     * 
     * <p>Собирает карточки, связанные с указанными пользователями команды,
     * которые были изменены после указанного момента времени.</p>
     * 
     * @param teamEmails список email пользователей команды
     * @param since момент времени, с которого собирать изменения
     */
    void collectCardsForTeam(List<String> teamEmails, LocalDateTime since);
    
    /**
     * Собрать карточки для всех пользователей.
     * 
     * <p>Собирает карточки для всех пользователей в системе,
     * которые были изменены после указанного момента времени.</p>
     * 
     * @param since момент времени, с которого собирать изменения
     */
    void collectCardsForAllUsers(LocalDateTime since);

}
