package ru.x5.devpulse.application.port.out;

import java.util.function.Supplier;

/**
 * Port out: явный transaction boundary для use case'ов.
 *
 * <p>Use case-ы живут вне Spring AOP (application-модуль не зависит от Spring — enforced
 * ArchUnit'ом), поэтому {@code @Transactional} на use case method не работает. Когда use case
 * должен охватить несколько operations в одну atomic unit, он явно оборачивает блок в
 * {@link #inTransaction(Supplier)}.</p>
 *
 * <p><b>Семантика:</b> propagation REQUIRED, isolation default (READ COMMITTED для Postgres).
 * Если внутри блока бросается unchecked exception — транзакция rollback'ится и exception
 * пробрасывается дальше.</p>
 *
 * <p><b>Когда НЕ нужно:</b> если операция одиночная и адаптер сам помечен {@code @Transactional}
 * — этот wrapper лишний. Использовать для составных операций (например cleanup + recompute,
 * которые должны быть атомарны вместе).</p>
 */
public interface TransactionRunner {

    /**
     * Выполняет блок в новой транзакции (или присоединяется к существующей).
     *
     * @param work код для выполнения
     * @param <T>  тип возвращаемого результата
     * @return результат {@code work.get()}
     */
    <T> T inTransaction(Supplier<T> work);

    /** Удобная перегрузка для void-блоков. */
    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
