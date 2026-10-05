package com.planet.joboutbox.data.entity;

import com.planet.joboutbox.domain.OutBoxState;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "OUTBOX_IMPORT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxImportState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_import_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private OutBoxState state;



}
