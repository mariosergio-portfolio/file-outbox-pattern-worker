package com.planet.joboutbox.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Slf4j
public class RabbitMQConfig {

    /*
     *  All queues, exchanges and bindings are configured in the processor-service, so we don't need to configure them here.
     *  This service is only a producer of messages, so we only need to configure the RabbitTemplate and the message converter.
     *
     *  Import and export publishers each get their own RabbitTemplate bean (rather than sharing one) because
     *  RabbitTemplate.setConfirmCallback() is a single global slot on the template: sharing one bean would mean
     *  either publisher's confirm callback overwrites/applies to the other's messages.
     * */

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean(name = "importRabbitTemplate")
    @Primary
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        return buildTemplate(connectionFactory, messageConverter);
    }

    @Bean(name = "exportRabbitTemplate")
    public RabbitTemplate exportRabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        return buildTemplate(connectionFactory, messageConverter);
    }

    private RabbitTemplate buildTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setMessageConverter(messageConverter);

        return template;
    }
}

