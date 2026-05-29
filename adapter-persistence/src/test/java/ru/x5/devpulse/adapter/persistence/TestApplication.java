package ru.x5.devpulse.adapter.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Лёгкий @SpringBootApplication только для IT-тестов adapter-persistence.
 *
 * <p>Лежит в корневом пакете адаптера, чтобы тесты в подпакетах находили его
 * через стандартный механизм Spring Test (поиск {@code @SpringBootConfiguration}
 * вверх по пакетам от теста). Сканирует только {@code adapter.persistence},
 * не тянет остальные модули.</p>
 */
@SpringBootApplication
public class TestApplication {
}
