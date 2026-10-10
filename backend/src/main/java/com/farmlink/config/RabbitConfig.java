package com.farmlink.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "farmlink.events";
    public static final String QUEUE = "farmlink.notifications";
    public static final String ROUTING_KEY = "farmlink.event";

    @Bean
    public TopicExchange farmlinkExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue farmlinkQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding farmlinkBinding(TopicExchange farmlinkExchange, Queue farmlinkQueue) {
        return BindingBuilder.bind(farmlinkQueue).to(farmlinkExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
