package com.planet.joboutbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(OutboxProperties outboxProperties) {
        return RestClient.builder()
                .baseUrl(outboxProperties.getProcessorServiceUrl())
                .build();
    }
}
