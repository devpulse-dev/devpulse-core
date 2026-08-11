package ru.x5.devpulse.domain.model.stats;

import java.util.Objects;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Вклад одного автора в ячейку почасовой матрицы.
 *
 * <p>До контракта 3.12.0 ячейка была анонимной: параметры {@code email}/{@code team}
 * фильтруют выборку на входе, но в ответе автора не оставалось, и построить drill-down
 * по ячейке было не из чего — дневные агрегаты знают автора, но не знают часа.</p>
 *
 * @param email      автор коммитов
 * @param commits    не-мердж коммиты этого автора в данной ячейке
 * @param addedLines добавленные строки этого автора в данной ячейке
 */
public record HourlyBucketAuthor(Email email, long commits, long addedLines) {

    public HourlyBucketAuthor {
        Objects.requireNonNull(email, "email required");
        if (commits < 0 || addedLines < 0) {
            throw new IllegalArgumentException(
                    "counters must be non-negative: commits=" + commits + " addedLines=" + addedLines);
        }
    }
}
