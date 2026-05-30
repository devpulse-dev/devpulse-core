package ru.x5.devpulse.adapter.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.x5.devpulse.adapter.rest.api.model.KaitenCard;

/**
 * {@code domain.kaiten.KaitenCard} → {@link KaitenCard}.
 *
 * <p>{@code cardType}, {@code columnStatus}, {@code closed} — computed-методы record'а,
 * не record-components → ставим явно через expression. Domain enum
 * ({@code KaitenCardType}, {@code KaitenColumnStatus}) → target enum по имени значения.</p>
 *
 * <p>Конвертеры разворачивают {@code KaitenCardId}/{@code KaitenUserId} → {@code Long},
 * {@code String url} → {@code URI}, {@code List<KaitenUserId> memberIds} → {@code List<Long>}.</p>
 */
@Mapper(componentModel = "spring",
        implementationName = "RestKaitenCardMapperImpl",
        uses = DomainTypeConverters.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface KaitenCardMapper {

    @Mapping(target = "cardType",
            expression = "java(ru.x5.devpulse.adapter.rest.api.model.KaitenCard.CardTypeEnum.valueOf(c.cardType().name()))")
    @Mapping(target = "columnStatus",
            expression = "java(ru.x5.devpulse.adapter.rest.api.model.KaitenCard.ColumnStatusEnum.valueOf(c.columnStatus().name()))")
    @Mapping(target = "closed", expression = "java(c.isClosed())")
    KaitenCard toDto(ru.x5.devpulse.domain.model.kaiten.KaitenCard c);
}
