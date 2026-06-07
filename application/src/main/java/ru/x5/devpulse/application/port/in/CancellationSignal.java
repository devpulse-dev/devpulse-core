package ru.x5.devpulse.application.port.in;

/**
 * Сигнал кооперативной отмены сбора. Оркестратор конструирует его от {@code runId}
 * (backed by {@code CollectionRunRepository.isCancelRequested}) и прокидывает в фазы,
 * которые опрашивают его на безопасных точках (между репозиториями / фазами).
 *
 * <p>Не порт в смысле «реализуется адаптером» — это узкий value-контракт, создаётся
 * лямбдой в application-слое. Лежит в {@code port.in}, чтобы на него мог ссылаться
 * use-case-порт без зависимости на {@code port.out}.</p>
 */
@FunctionalInterface
public interface CancellationSignal {

    /** {@code true} — оператор запросил отмену; фаза должна остановиться в ближайшей safe-точке. */
    boolean cancelled();

    /** Сигнал, который никогда не отменяется (для тестов и вызовов без поддержки отмены). */
    CancellationSignal NEVER = () -> false;
}
