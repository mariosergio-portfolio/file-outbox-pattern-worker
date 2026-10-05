package com.planet.joboutbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planet.joboutbox.config.OutboxProperties;
import com.planet.joboutbox.data.entity.OutboxImport;
import com.planet.joboutbox.domain.OutBoxState;
import com.planet.joboutbox.messaging.ImportEvent;
import com.planet.joboutbox.messaging.ImportEventPublisher;
import com.planet.joboutbox.data.repository.OutboxImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxImportWatcherJob {

    private final OutboxImportRepository outboxImportRepository;
    private final ImportEventPublisher importEventPublisher;
    private final OutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRateString = "${outbox.schedule-rate-ms:30000}")
    public void watch() {

        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(outboxProperties.getStaleThresholdMinutes());
        List<OutboxImport> staleRecords = outboxImportRepository.findStaleByState(OutBoxState.PENDING, staleBefore);

        if (staleRecords.isEmpty()) {
            log.debug("OutboxWatcherJob: no stale PENDING records found");
            return;
        }

        for (OutboxImport staleRecord : staleRecords) {
            try {
                ImportEvent event = objectMapper.readValue(staleRecord.getPayload(), ImportEvent.class);
                importEventPublisher.publishSync(event, staleRecord.getId(), staleRecord.getRoutingKey());

                staleRecord.setState(OutBoxState.SENT);
                staleRecord.setProcessedAt(LocalDateTime.now());
                outboxImportRepository.save(staleRecord);

            } catch (Exception e) {
                log.error("OutboxWatcherJob failed to process outbox record id={}, importId={}: {}",
                        staleRecord.getId(), staleRecord.getImportId(), e.getMessage(), e);

                break; // interrupt the loop to avoid publishing further records, to assure the sequence of events is preserved.
            }
        }
    }
}
