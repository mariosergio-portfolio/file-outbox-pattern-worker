package com.planet.joboutbox.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "outbox")
@Getter
@Setter
public class OutboxProperties {
    private long staleThresholdMinutes = 1;
    private String processorServiceUrl = "http://localhost:8081";
}
