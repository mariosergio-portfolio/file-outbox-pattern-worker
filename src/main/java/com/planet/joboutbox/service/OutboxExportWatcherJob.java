package com.planet.joboutbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planet.joboutbox.config.OutboxProperties;
import com.planet.joboutbox.data.entity.OutboxExport;
import com.planet.joboutbox.data.repository.OutboxExportRepository;
import com.planet.joboutbox.domain.OutBoxState;
import com.planet.joboutbox.messaging.ExportEvent;
import com.planet.joboutbox.messaging.ExportEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxExportWatcherJob {

    private final OutboxExportRepository outboxExportRepository;
    private final ExportEventPublisher exportEventPublisher;
    private final OutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRateString = "${outbox.schedule-rate-ms:30000}")
    public void watch() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(outboxProperties.getStaleThresholdMinutes());
        List<OutboxExport> staleRecords = outboxExportRepository.findStaleByState(OutBoxState.PENDING, staleBefore);

        if (staleRecords.isEmpty()) {
            log.debug("ExportOutboxWatcherJob: no stale PENDING records found");
            return;
        }

        for (OutboxExport record : staleRecords) {
            try {
                ExportEvent event = objectMapper.readValue(record.getPayload(), ExportEvent.class);
                exportEventPublisher.publish(event, record.getId());

            } catch (Exception e) {
                log.error("ExportOutboxWatcherJob failed to process outbox record id={}, exportId={}: {}",
                        record.getId(), record.getExportId(), e.getMessage(), e);
            }
        }
    }
}
