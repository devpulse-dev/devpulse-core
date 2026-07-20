package ru.x5.devpulse.domain.model.user;

/**
 * Роль пользователя для RBAC. Деривится, не хранится в БД (см. ADR-13): источники —
 * конфиг {@code auth.admins} (ADMIN) и существующий флаг {@code is_lead} (TEAMLEAD).
 */
public enum Role {
    MEMBER,
    TEAMLEAD,
    ADMIN;

    /**
     * Приоритет: ADMIN (конфиг) &gt; TEAMLEAD (is_lead) &gt; MEMBER.
     *
     * @param admin email пользователя в списке {@code auth.admins}
     * @param lead  у пользователя поднят {@code is_lead}
     */
    public static Role resolve(boolean admin, boolean lead) {
        if (admin) return ADMIN;
        if (lead) return TEAMLEAD;
        return MEMBER;
    }

    /** ADMIN и TEAMLEAD — полный доступ; MEMBER — ограниченный. */
    public boolean isElevated() {
        return this == ADMIN || this == TEAMLEAD;
    }
}
