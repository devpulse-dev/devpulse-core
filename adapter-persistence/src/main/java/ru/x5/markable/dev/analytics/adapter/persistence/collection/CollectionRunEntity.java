package ru.x5.markable.dev.analytics.adapter.persistence.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.x5.markable.dev.analytics.domain.model.collection.CollectionStatus;

@Entity
@Table(name = "collection_run")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionRunEntity {

    @Id
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "since_date", nullable = false)
    private LocalDateTime sinceDate;

    @Column(name = "until_date", nullable = false)
    private LocalDateTime untilDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CollectionStatus status;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}
