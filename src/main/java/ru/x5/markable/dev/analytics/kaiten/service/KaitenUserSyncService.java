package ru.x5.markable.dev.analytics.kaiten.service;

import java.util.List;
import java.util.Optional;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenUser;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenUserDto;

public interface KaitenUserSyncService {

    void syncAllUsers();
    void syncUserByEmail(String email);

    void syncUsersByEmails(List<String> emails);
    KaitenUser saveOrUpdate(KaitenUserDto dto);

    Optional<KaitenUser> findByEmail(String email);

    List<KaitenUser> getAllUsers();

}
