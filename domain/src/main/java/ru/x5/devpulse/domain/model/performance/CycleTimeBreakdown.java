package ru.x5.devpulse.domain.model.performance;

/**
 * Cycle-time раздельно по дефектам и разработке — длительность у них разная
 * (разработка обычно дольше), общая медиана была бы некорректной.
 */
public record CycleTimeBreakdown(CycleTime defects, CycleTime development) {

    public static final CycleTimeBreakdown EMPTY =
            new CycleTimeBreakdown(CycleTime.EMPTY, CycleTime.EMPTY);
}
