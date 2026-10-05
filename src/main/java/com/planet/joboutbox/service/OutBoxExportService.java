package com.planet.joboutbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.planet.joboutbox.data.entity.OutboxExport;
import com.planet.joboutbox.data.entity.OutboxExportState;
import com.planet.joboutbox.data.repository.OutboxExportRepository;
import com.planet.joboutbox.data.repository.OutboxExportStateRepository;
import com.planet.joboutbox.domain.OutBoxState;
import com.planet.joboutbox.messaging.ExportEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutBoxExportService {

    private final OutboxExportRepository outboxExportRepository;
    private final OutboxExportStateRepository outboxExportStateRepository;
    private final ObjectMapper objectMapper;


    public void changeStatus(Long outBoxId, OutBoxState stateToChange) {
        try {

            OutboxExportState outbox = OutboxExportState.builder()
                    .id(outBoxId)
                    .state(stateToChange)
                    .build();
            outboxExportStateRepository.save(outbox);

        } catch (Exception e) {
            log.error("Failed to change outbox event to delete for outBoxId: " + outBoxId, e);
        }
    }
}
