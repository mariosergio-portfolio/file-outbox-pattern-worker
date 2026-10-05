package com.planet.joboutbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class JobOutboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobOutboxApplication.class, args);
    }
}
