package ru.x5.markable.dev.analytics.gitlab.exception;

import lombok.Getter;

/**
 * Исключение, выбрасываемое при ошибке анализа Git-репозитория.
 * 
 * <p>Содержит информацию о репозитории, который не удалось проанализировать, и причину ошибки.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Getter
public class RepositoryAnalysisException extends AnalysisException {

  /**
   * Путь или имя репозитория, который не удалось проанализировать.
   */
  private final String repository;

  /**
   * Создает исключение с информацией о неудачном анализе репозитория.
   * 
   * @param repository путь или имя репозитория
   * @param cause причина ошибки
   */
  public RepositoryAnalysisException(String repository, Throwable cause) {
    super("Failed to analyze repository: " + repository, cause);
    this.repository = repository;
  }

}
