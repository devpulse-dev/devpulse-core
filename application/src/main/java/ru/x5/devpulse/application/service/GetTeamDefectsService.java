package ru.x5.devpulse.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.GetTeamDefectsUseCase;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.performance.DefectDetail;
import ru.x5.devpulse.domain.model.performance.DefectMember;
import ru.x5.devpulse.domain.model.performance.TeamDefectsReport;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;
import ru.x5.devpulse.domain.service.DefectPeriodAssembler;

/**
 * Дефекты команды по приоритету за периоды. Оркестрация: команда → участники (kaiten id) →
 * live-стрим карточек из Kaiten одним проходом → дедуп по id → раскладка по периодам в чистом
 * {@link DefectPeriodAssembler}.
 *
 * <p><b>Уникальность дефектов</b> (ключевое требование): одна карточка может содержать несколько
 * участников команды и потому вернуться из {@code streamCards} несколько раз (по memberFilter).
 * Дедуп по {@link KaitenCardId} гарантирует счёт «один дефект — один раз».</p>
 *
 * <p><b>Один проход по Kaiten</b>: тянем карточки, обновлённые начиная с самой ранней {@code from}
 * среди всех периодов. Дефект с {@code createdAt >= minFrom} всегда имеет {@code updatedAt >= createdAt},
 * поэтому попадает в выборку — периоды режем уже в памяти, повторно Kaiten не дёргаем.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class GetTeamDefectsService implements GetTeamDefectsUseCase {

    private final UnifiedUserRepository unifiedUserRepository;
    private final KaitenGateway kaitenGateway;

    @Override
    public TeamDefectsReport get(String team, List<Period> periods) {
        if (periods.isEmpty()) {
            return new TeamDefectsReport(team, List.of(), List.of());
        }

        // Один findAll: и участники команды (kaiten id для фильтра), и справочник для резолва
        // ЛЮБЫХ участников карточек (аватарки «кто был» — не только текущей команды).
        List<UnifiedUser> allUsers = unifiedUserRepository.findAll();
        Map<KaitenUserId, UnifiedUser> byKaitenId = new LinkedHashMap<>();
        for (UnifiedUser u : allUsers) {
            u.kaiten().ifPresent(kid -> byKaitenId.putIfAbsent(kid, u));
        }

        List<KaitenUserId> members = allUsers.stream()
                .filter(u -> team.equals(u.team()))
                .map(UnifiedUser::kaiten)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .toList();

        // Нет привязанных к Kaiten участников → все периоды пустые (без обращения к Kaiten:
        // streamCards с пустым memberFilter означал бы полный скан org-справочника).
        if (members.isEmpty()) {
            log.info("defects team='{}': нет участников с kaiten_id — пустой отчёт", team);
            return new TeamDefectsReport(
                    team, DefectPeriodAssembler.countByPeriods(List.of(), periods), List.of());
        }

        LocalDate minFrom = periods.stream().map(Period::from).min(LocalDate::compareTo).orElseThrow();
        LocalDateTime updatedAfter = minFrom.atStartOfDay();

        // Дедуп по id карточки: memberFilter может вернуть одну карточку несколько раз.
        Map<KaitenCardId, KaitenCard> unique = new LinkedHashMap<>();
        kaitenGateway.streamCards(members, updatedAfter, page -> {
            for (KaitenCard card : page) {
                unique.putIfAbsent(card.id(), card);
            }
        });

        List<KaitenCard> cards = new ArrayList<>(unique.values());
        log.info("defects team='{}': {} участников, {} уникальных карточек, {} периодов",
                team, members.size(), cards.size(), periods.size());

        List<DefectDetail> defects = DefectPeriodAssembler.uniqueDefectsInAnyPeriod(cards, periods).stream()
                .map(card -> toDetail(card, byKaitenId))
                .toList();

        return new TeamDefectsReport(
                team, DefectPeriodAssembler.countByPeriods(cards, periods), defects);
    }

    /** Карточка-дефект → DefectDetail; участники резолвятся в unified_user (без резолва — пропускаются). */
    private static DefectDetail toDetail(KaitenCard card, Map<KaitenUserId, UnifiedUser> byKaitenId) {
        List<DefectMember> members = card.memberIds().stream()
                .distinct()
                .map(byKaitenId::get)
                .filter(java.util.Objects::nonNull)
                .map(u -> new DefectMember(u.email(), u.displayName().orElse(null), u.avatar().orElse(null)))
                .toList();
        return new DefectDetail(
                card.id(), card.title(), card.url(), card.createdAt(), card.aiAgent(), members);
    }
}
