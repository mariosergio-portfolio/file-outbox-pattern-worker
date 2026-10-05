package com.planet.joboutbox.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ImportEventPublisher {


   private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.import}")
    private String importExchange;

    @Value("${rabbitmq.publish-confirm-timeout-ms:5000}")
    private long publishConfirmTimeoutMs;

    public ImportEventPublisher(@Qualifier("importRabbitTemplate") RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishSync(ImportEvent event, Long outBoxId, String routingKey) {
        CorrelationData correlationData = new CorrelationData(String.valueOf(outBoxId));

        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(importExchange, routingKey, event, correlationData);
            operations.waitForConfirmsOrDie(publishConfirmTimeoutMs);
            return null;
        }, null, null);

        log.info("Broker accepted message with importId={}", event.importId());

    }

}
