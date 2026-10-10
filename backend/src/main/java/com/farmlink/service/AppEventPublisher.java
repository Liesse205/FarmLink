package com.farmlink.service;

import com.farmlink.config.RabbitConfig;
import com.farmlink.dto.AppEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(AppEvent event) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event);
    }
}
