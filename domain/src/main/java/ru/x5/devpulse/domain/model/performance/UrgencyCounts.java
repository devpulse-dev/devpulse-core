package ru.x5.devpulse.domain.model.performance;

/** Счётчики дефектов по срочности (снапшот за период). */
public record UrgencyCounts(int critical, int high, int medium, int low, int unknown) {

    public static final UrgencyCounts EMPTY = new UrgencyCounts(0, 0, 0, 0, 0);

    public int total() {
        return critical + high + medium + low + unknown;
    }

    /** «Горящие» — критичные + высокие. */
    public int criticalHigh() {
        return critical + high;
    }
}
