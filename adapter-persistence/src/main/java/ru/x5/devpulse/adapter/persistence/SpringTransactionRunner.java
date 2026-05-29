package ru.x5.devpulse.adapter.persistence;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.x5.devpulse.application.port.out.TransactionRunner;

/**
 * Реализация {@link TransactionRunner} поверх Spring {@link TransactionTemplate}.
 *
 * <p>Дефолты: propagation REQUIRED, isolation default (для Postgres — READ COMMITTED).
 * Unchecked exception внутри блока → rollback + пробрасывается дальше. Checked exception
 * мы не используем — все ошибки доменной логики являются {@code RuntimeException}.</p>
 */
@Component
class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    SpringTransactionRunner(PlatformTransactionManager txManager) {
        this.template = new TransactionTemplate(txManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
