package ru.x5.devpulse.application.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Реализация {@link SyncKaitenUsersUseCase}.
 *
 * <p><b>Идея:</b> синхронизируем не весь оргсправочник Kaiten, а только тех, кто реально
 * есть у нас в {@code unified_user} (наши коммитеры). Раньше тянули и персистили всех
 * пользователей Kaiten в write-only таблицу {@code kaiten_user} — рудимент удалён.</p>
 *
 * <p><b>Алгоритм</b> (на входе — {@code unified_user}, поделённый на привязанных и нет):</p>
 * <ol>
 *   <li><b>Непривязанные</b> (нет {@code kaiten_id}): их kaiten id нам неизвестен — резолвим
 *       одним полным сканом {@code streamUsers} и матчим по email. Скан идёт только если
 *       непривязанные вообще есть.</li>
 *   <li><b>Привязанные</b> (есть {@code kaiten_id}): точечно обновляем name/avatar через
 *       {@code fetchUsersByIds} — но не чаще раза в {@code userRefreshInterval}. Свежих
 *       (по {@code lastSyncedAt}) не трогаем — в steady-state API не дёргаем вовсе.</li>
 * </ol>
 *
 * <p>Линковка всегда идёт через {@code updateKaitenId} по email — он же проставляет
 * {@code lastSyncedAt}, на котором держится staleness-гейт. Без явных транзакций на этом
 * слое: каждый adapter-метод сам {@code @Transactional}, retry идемпотентен.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class SyncKaitenUsersService implements SyncKaitenUsersUseCase {

    private final KaitenGateway kaitenGateway;
    private final UnifiedUserRepository unifiedUserRepository;
    /**
     * Порог устаревания привязанных: рефрешим name/avatar не чаще раза в этот интервал.
     * Приходит из {@code kaiten.api.user-refresh-interval} (см. KaitenProperties), дефолт 3 дня.
     */
    private final Duration userRefreshInterval;

    @Override
    public int syncAll() {
        log.info("Старт синхронизации пользователей Kaiten");
        List<UnifiedUser> all = unifiedUserRepository.findAll();
        if (all.isEmpty()) {
            log.info("В unified_user нет пользователей — синхронизировать нечего");
            return 0;
        }

        List<UnifiedUser> unlinked = new ArrayList<>();
        List<UnifiedUser> staleLinked = new ArrayList<>();
        LocalDateTime staleBefore = LocalDateTime.now().minus(userRefreshInterval);
        for (UnifiedUser u : all) {
            if (u.kaitenId() == null) {
                unlinked.add(u);
            } else if (isStale(u, staleBefore)) {
                staleLinked.add(u);
            }
        }

        int resolved = resolveUnlinked(unlinked);
        int refreshed = refreshLinked(staleLinked);

        log.info("Синхронизация Kaiten завершена: привязано новых {}, обновлено привязанных {} "
                + "(привязанных свежих пропущено {})", resolved, refreshed,
                all.size() - unlinked.size() - staleLinked.size());
        return resolved + refreshed;
    }

    /**
     * Резолв непривязанных по email — единственный сценарий, где нужен полный скан Kaiten
     * (id неизвестен). Скан не запускается, если непривязанных нет.
     */
    private int resolveUnlinked(List<UnifiedUser> unlinked) {
        if (unlinked.isEmpty()) return 0;

        Set<Email> wanted = new HashSet<>();
        for (UnifiedUser u : unlinked) {
            if (u.email() != null) wanted.add(u.email());
        }
        if (wanted.isEmpty()) return 0;

        int[] linked = {0};
        log.info("Непривязанных пользователей: {} — полный скан Kaiten для резолва по email", wanted.size());
        kaitenGateway.streamUsers(page -> {
            for (KaitenUser ku : page) {
                Email email = ku.email();
                // remove() == был в наборе → это наш ещё не привязанный пользователь.
                if (email != null && wanted.remove(email)) {
                    unifiedUserRepository.updateKaitenId(email, ku.id(), ku.fullName(), ku.avatarUrl());
                    linked[0]++;
                }
            }
        });
        if (!wanted.isEmpty()) {
            log.info("Не нашлись в Kaiten {} email'ов — остаются без kaiten_id", wanted.size());
        }
        return linked[0];
    }

    /**
     * Рефреш name/avatar для уже привязанных: точечно по id, без выкачки всего справочника.
     * Деактивированные/удалённые в Kaiten просто не вернутся — их kaiten_id остаётся как есть.
     */
    private int refreshLinked(List<UnifiedUser> staleLinked) {
        if (staleLinked.isEmpty()) return 0;

        List<KaitenUserId> ids = new ArrayList<>(staleLinked.size());
        for (UnifiedUser u : staleLinked) ids.add(u.kaitenId());

        Map<Long, KaitenUser> byId = new HashMap<>();
        for (KaitenUser ku : kaitenGateway.fetchUsersByIds(ids)) {
            byId.put(ku.id().value(), ku);
        }

        int refreshed = 0;
        for (UnifiedUser u : staleLinked) {
            KaitenUser ku = byId.get(u.kaitenId().value());
            // Линкуем по email из unified_user (а не из Kaiten): ключ матчинга — наш email,
            // он стабилен; email в Kaiten мог отличаться/смениться.
            if (ku != null && u.email() != null) {
                unifiedUserRepository.updateKaitenId(u.email(), ku.id(), ku.fullName(), ku.avatarUrl());
                refreshed++;
            }
        }
        return refreshed;
    }

    private static boolean isStale(UnifiedUser u, LocalDateTime staleBefore) {
        LocalDateTime last = u.lastSyncedAt();
        return last == null || last.isBefore(staleBefore);
    }
}
