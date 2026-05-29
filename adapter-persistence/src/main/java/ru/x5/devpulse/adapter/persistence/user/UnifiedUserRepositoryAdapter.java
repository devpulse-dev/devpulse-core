package ru.x5.devpulse.adapter.persistence.user;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@Component
@Log4j2
@RequiredArgsConstructor
class UnifiedUserRepositoryAdapter implements UnifiedUserRepository {

    private final UnifiedUserJpaRepository jpa;
    private final UnifiedUserEntityMapper mapper;

    @Override
    public Optional<UnifiedUser> findByEmail(Email email) {
        // Email.value() уже lowercase (инвариант value object'а, см. EmailTest).
        // В БД email тоже lowercase (миграция 020 + UNIQUE INDEX по LOWER(email)).
        return jpa.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Map<Email, Long> findOrCreateAll(Collection<Email> emails) {
        if (emails == null || emails.isEmpty()) return Map.of();

        // Email.value() — уже lowercase (инвариант VO). Дополнительной нормализации не нужно.
        Set<String> normalized = new HashSet<>();
        for (Email e : emails) {
            if (e != null && e.value() != null) normalized.add(e.value());
        }
        if (normalized.isEmpty()) return Map.of();

        Map<Email, Long> result = new HashMap<>(normalized.size());

        // 1 SELECT для существующих
        for (UnifiedUserEntity u : jpa.findByEmailIn(normalized)) {
            result.put(new Email(u.getEmail()), u.getId());
        }

        // Создаём недостающих batch-INSERT'ом
        List<UnifiedUserEntity> toCreate = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String email : normalized) {
            if (!containsEmail(result, email)) {
                toCreate.add(UnifiedUserEntity.builder()
                        .email(email)                  // уже lowercase
                        .username(localPart(email))
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
        }
        if (toCreate.isEmpty()) return result;

        try {
            for (UnifiedUserEntity saved : jpa.saveAll(toCreate)) {
                result.put(new Email(saved.getEmail()), saved.getId());
            }
        } catch (DataIntegrityViolationException ex) {
            // Конкурирующее создание — перечитаем недостающих
            log.debug("Concurrent insert detected in unified_user, refetching");
            Set<String> stillMissing = new HashSet<>(normalized);
            result.keySet().forEach(e -> stillMissing.remove(e.value()));
            for (UnifiedUserEntity u : jpa.findByEmailIn(stillMissing)) {
                result.put(new Email(u.getEmail()), u.getId());
            }
        }
        return result;
    }

    @Override
    public List<UnifiedUser> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<UnifiedUser> findByEmails(Collection<Email> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        // Email.value() — lowercase (инвариант VO). distinct() убирает дубли по идентичной строке.
        List<String> values = emails.stream()
                .filter(e -> e != null && e.value() != null)
                .map(Email::value)
                .distinct()
                .toList();
        if (values.isEmpty()) return List.of();
        return jpa.findByEmailIn(values).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void updateKaitenId(Email email, KaitenUserId kaitenId, String name, String avatarUrl) {
        // Email.value() — lowercase (инвариант VO).
        jpa.findByEmail(email.value()).ifPresent(user -> {
            user.setKaitenId(kaitenId.value());
            user.setName(name);
            user.setAvatarUrl(avatarUrl);
            user.setUpdatedAt(LocalDateTime.now());
            jpa.save(user);
        });
    }

    private static boolean containsEmail(Map<Email, Long> m, String value) {
        for (Email e : m.keySet()) {
            if (e.value().equals(value)) return true;
        }
        return false;
    }

    private static String localPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
