package com.planet.joboutbox.data.entity;

import com.planet.joboutbox.domain.ImportState;
import com.planet.joboutbox.domain.OutBoxState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "OUTBOX_IMPORT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_import_id")
    private Long id;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private OutBoxState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
