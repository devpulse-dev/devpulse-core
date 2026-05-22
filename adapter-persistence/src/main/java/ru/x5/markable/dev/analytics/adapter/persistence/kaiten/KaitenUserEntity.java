package ru.x5.markable.dev.analytics.adapter.persistence.kaiten;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kaiten_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KaitenUserEntity {

    /** Kaiten user id, выставляется снаружи (Kaiten API), не auto-generated. */
    @Id
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "name")
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
