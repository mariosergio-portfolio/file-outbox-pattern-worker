package com.planet.joboutbox.messaging;

import com.planet.joboutbox.domain.OutBoxState;
import com.planet.joboutbox.service.OutBoxExportService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component

@Slf4j
public class ExportEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final OutBoxExportService outBoxService;

    @Value("${rabbitmq.exchange.export}")
    private String exportExchange;

    @Value("${rabbitmq.routing-key.export}")
    private String exportRoutingKey;

    public ExportEventPublisher(@Qualifier("exportRabbitTemplate") RabbitTemplate rabbitTemplate, OutBoxExportService outBoxService) {
        this.rabbitTemplate = rabbitTemplate;
        this.outBoxService = outBoxService;
    }

    @PostConstruct
    void registerConfirmCallback() {
        // SET STATE OF OUTBOX MESSAGE TO DELETED IF BROKER ACCEPTS THE MESSAGE (ONLY FOR EXPORT EVENTS)
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("Broker accepted message with id={}", (correlationData == null) ? "<no correlation id>" : correlationData.getId());
                outBoxService.changeStatus(Long.valueOf(correlationData.getId()), OutBoxState.SENT);
            } else {
                log.error("Broker rejected message with id={}, cause={}", (correlationData == null) ? "<no correlation id>" : correlationData.getId(), cause);
            }
        });
    }

    @Async
    public void publish(ExportEvent event, Long outBoxId) {

        log.info("Published export event: exportId={}, state={}", event.exportId(), event.state());

        rabbitTemplate.convertAndSend(
                exportExchange,
                exportRoutingKey,
                event,
                new CorrelationData(outBoxId.toString())
        );

    }
}
