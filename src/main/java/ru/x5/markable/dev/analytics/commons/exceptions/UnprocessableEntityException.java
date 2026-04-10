package ru.x5.markable.dev.analytics.commons.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Исключение для ошибок обработки сущности (HTTP 422).
 * 
 * <p>Выбрасывается, когда запрос был успешно получен и понят сервером,
 * но не может быть обработан из-за семантических ошибок.</p>
 * 
 * <p>Используется для ситуаций, когда:</p>
 * <ul>
 *   <li>Некорректные данные в запросе</li>
 *   <li>Нарушение бизнес-правил</li>
 *   <li>Невозможность выполнить операцию с текущим состоянием данных</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public class UnprocessableEntityException extends ApiException {

    /**
     * Создает исключение с форматированным сообщением из шаблона.
     * 
     * @param messageTemplate шаблон сообщения об ошибке
     * @param args аргументы для подстановки в шаблон
     */
    public UnprocessableEntityException(MessageTemplate messageTemplate, Object... args) {
        super(messageTemplate.getText(args));
    }

    /**
     * Возвращает HTTP статус для этого типа исключения.
     * 
     * @return {@link HttpStatus#UNPROCESSABLE_ENTITY} (422)
     */
    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
