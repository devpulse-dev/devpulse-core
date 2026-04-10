package ru.x5.markable.dev.analytics.gitlab.exception;

import lombok.Getter;

/**
 * Исключение, выбрасываемое при ошибке выполнения Git-команды.
 * 
 * <p>Содержит информацию о команде, которая не удалась, и причину ошибки.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Getter
public class GitExecutionException extends AnalysisException {

  /**
   * Git-команда, которая не удалась.
   */
  private final String command;

  /**
   * Создает исключение с информацией о неудачной Git-команде.
   * 
   * @param command Git-команда, которая не удалась
   * @param cause причина ошибки
   */
  public GitExecutionException(String command, Throwable cause) {
    super("Git command failed: " + command, cause);
    this.command = command;
  }
}
