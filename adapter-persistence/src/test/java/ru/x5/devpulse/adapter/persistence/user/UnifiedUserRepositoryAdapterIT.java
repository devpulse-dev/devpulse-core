package ru.x5.devpulse.adapter.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.x5.devpulse.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.Email;

@SpringBootTest
@DisplayName("UnifiedUserRepositoryAdapter (TestContainers + PostgreSQL)")
class UnifiedUserRepositoryAdapterIT extends PostgresContainerSupport {

    private static final Email BORIS = new Email("boris@x5.ru");
    private static final Email IVAN = new Email("ivan@x5.ru");
    private static final Email ANYA = new Email("anya@x5.ru");
    private static final Email PAVEL_RAW_CASE = new Email("Pavel@x5.ru");
    private static final Email PAVEL_UPPERCASE = new Email("PAVEL@X5.RU");
    private static final Email PAVEL_LOWERCASE = new Email("pavel@x5.ru");

    @Autowired
    UnifiedUserRepository repo;

    @Test
    @DisplayName("findOrCreateAll: создаёт недостающих пользователей одним батчем")
    void findOrCreateAllCreatesMissingInOneShot() {
        Map<Email, Long> result = repo.findOrCreateAll(List.of(BORIS, IVAN));

        assertAll("результат batch find-or-create",
                () -> assertThat(result)
                        .as("на каждый запрошенный email должна быть запись")
                        .containsKeys(BORIS, IVAN),
                () -> assertThat(result.get(BORIS))
                        .as("id Бориса должен быть положительный (BIGSERIAL)")
                        .isPositive(),
                () -> assertThat(result.get(IVAN))
                        .as("id Ивана должен быть положительный")
                        .isPositive(),
                () -> assertThat(result.get(BORIS))
                        .as("id'ы разных пользователей не совпадают")
                        .isNotEqualTo(result.get(IVAN)));
    }

    @Test
    @DisplayName("findOrCreateAll идемпотентен: повторный вызов возвращает тот же id")
    void findOrCreateAllIsIdempotent() {
        Map<Email, Long> firstCall = repo.findOrCreateAll(List.of(ANYA));
        Map<Email, Long> secondCall = repo.findOrCreateAll(List.of(ANYA));

        assertThat(secondCall.get(ANYA))
                .as("повторный вызов не должен создавать дубликат")
                .isEqualTo(firstCall.get(ANYA));
    }

    @Test
    @DisplayName("findByEmail нормализует регистр email перед поиском")
    void findByEmailNormalizesCase() {
        repo.findOrCreateAll(List.of(PAVEL_RAW_CASE));

        assertAll("поиск по email в разных регистрах",
                () -> assertThat(repo.findByEmail(PAVEL_UPPERCASE))
                        .as("UPPER-CASE email должен найти запись")
                        .isPresent(),
                () -> assertThat(repo.findByEmail(PAVEL_LOWERCASE))
                        .as("lower-case email должен найти запись")
                        .isPresent());
    }

    @Test
    @DisplayName("findOrCreateAll: пустой вход возвращает пустую карту")
    void findOrCreateAllReturnsEmptyMapForEmptyInput() {
        assertThat(repo.findOrCreateAll(List.of()))
                .as("при отсутствии запрошенных email возвращается пустая карта")
                .isEmpty();
    }
}
