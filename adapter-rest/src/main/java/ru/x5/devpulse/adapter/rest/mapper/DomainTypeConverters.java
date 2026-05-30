package ru.x5.devpulse.adapter.rest.mapper;

import java.net.URI;
import org.mapstruct.Mapper;
import ru.x5.devpulse.domain.common.TaskNumber;
import ru.x5.devpulse.domain.model.git.CommitHash;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

/**
 * Глобальные конвертеры между доменными value-объектами и скалярами generated DTO.
 *
 * <p>Доменные типы оборачивают идентификаторы (например {@link Email}, {@link CommitHash})
 * чтобы запретить случайное смешение в бизнес-логике. На REST-границе разворачиваем
 * обратно в {@code String}/{@code Long}.</p>
 *
 * <p>Каждый mapper из {@code mapper/} подключает этот класс через {@code uses}.
 * MapStruct автоматически выбирает методы по сигнатуре (source-type → target-type).</p>
 */
@Mapper(componentModel = "spring", implementationName = "RestDomainTypeConvertersImpl")
public interface DomainTypeConverters {

    // ───── value object → строка ─────────────────────────────────────────

    default String map(Email e) { return e == null ? null : e.value(); }
    default String map(CommitHash h) { return h == null ? null : h.value(); }
    default String map(RepoName r) { return r == null ? null : r.value(); }
    default String map(TaskNumber t) { return t == null ? null : t.value(); }

    // ───── value object → Long ───────────────────────────────────────────

    default Long map(KaitenUserId k) { return k == null ? null : k.value(); }
    default Long map(KaitenCardId c) { return c == null ? null : c.value(); }

    // ───── String → URI (для avatarUrl, url) ─────────────────────────────

    default URI stringToUri(String s) { return s == null ? null : URI.create(s); }

    // ───── Integer → Long widening (для gitlabId, typeId — поля API int32, domain long) ─

    default Long integerToLong(Integer i) { return i == null ? null : i.longValue(); }

    // Note: longToInteger удалён — контракт 1.1.0 перешёл на int64 для counters,
    // MapStruct теперь мапит long → long без narrowing. См. OAS-2 в REFACTORING.md.
}
