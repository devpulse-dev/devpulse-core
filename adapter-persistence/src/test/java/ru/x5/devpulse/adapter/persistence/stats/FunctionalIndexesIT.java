package ru.x5.devpulse.adapter.persistence.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.x5.devpulse.adapter.persistence.shared.PostgresContainerSupport;

/**
 * Защитный тест: проверяет что functional indexes из миграции 019 существуют.
 *
 * <p>Эти индексы нужны для производительности hot-query пересчёта daily_stats. Запросы
 * фильтруют по {@code LOWER(email)} и {@code CAST(commit_date AS DATE)} — обычные b-tree
 * по {@code email}/{@code commit_date} тут НЕ работают. Если кто-то «оптимизирует»
 * миграцию убрав эти индексы — production деградирует молча. Тест ловит регрессию.</p>
 *
 * <p>Тест НЕ проверяет что планировщик действительно использует индекс на типичном запросе
 * — для этого нужен достаточный объём данных, который рендерил бы seq scan хуже indexscan.
 * На пустой таблице планировщик правомерно выбирает seq scan. Достаточно проверить наличие.</p>
 */
@SpringBootTest
@DisplayName("Functional composite indexes (миграция 019)")
class FunctionalIndexesIT extends PostgresContainerSupport {

    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest(name = "[{index}] {0}.{1}")
    @CsvSource({
            "commit_details, idx_commit_details_email_lower_date",
            "daily_author_stats, idx_daily_stats_email_lower_date"
    })
    @DisplayName("Индекс существует в pg_indexes")
    void indexExists(String table, String indexName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = ? AND indexname = ?",
                Integer.class, table, indexName);
        assertThat(count)
                .as("индекс %s на %s должен существовать (миграция 019)", indexName, table)
                .isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] {1} содержит {2}")
    @CsvSource({
            "commit_details, idx_commit_details_email_lower_date, lower",
            "commit_details, idx_commit_details_email_lower_date, commit_date",
            "daily_author_stats, idx_daily_stats_email_lower_date, lower",
            "daily_author_stats, idx_daily_stats_email_lower_date, date"
    })
    @DisplayName("Определение индекса включает функциональное выражение")
    void indexDefinitionContainsExpression(String table, String indexName, String fragment) {
        String def = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = ? AND indexname = ?",
                String.class, table, indexName);
        assertThat(def)
                .as("определение индекса должно содержать %s", fragment)
                .containsIgnoringCase(fragment);
    }
}
