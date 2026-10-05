package com.planet.joboutbox.data.entity;


import com.planet.joboutbox.domain.OutBoxState;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "OUTBOX_EXPORT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxExportState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_export_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private OutBoxState state;



}
