package ru.x5.devpulse.application.port.out;

/**
 * Валидный пользователь GitLab, но без доступа уровня {@code Developer} ни к одному из
 * отслеживаемых проектов и не в списке {@code auth.admins}. На REST-слое маппится в 403.
 *
 * <p>В {@code port.out} (а не {@code port.in}) по конвенции hexagonal-теста: исключения для
 * типизации ошибок живут рядом с портами оборудования (прецедент {@code
 * CollectionAlreadyRunningException}). Use-case {@code port.in} не зависит от {@code port.out},
 * поэтому в сигнатуре не объявляется (unchecked).</p>
 */
public class ProjectAccessDeniedException extends RuntimeException {

    public ProjectAccessDeniedException(String message) {
        super(message);
    }
}
