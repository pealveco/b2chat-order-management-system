package com.b2chat.ordermanagement.notification;

import com.b2chat.ordermanagement.model.order.OrderPlacedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Sinks;

@Configuration
public class OrderEventBusConfig {
    @Bean
    public Sinks.Many<OrderPlacedEvent> orderPlacedSink() {
        return Sinks.many().multicast().<OrderPlacedEvent>onBackpressureBuffer();
    }
}
