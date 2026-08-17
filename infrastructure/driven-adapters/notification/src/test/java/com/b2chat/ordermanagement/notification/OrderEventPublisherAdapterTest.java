package com.b2chat.ordermanagement.notification;

import com.b2chat.ordermanagement.model.order.OrderCompletedEvent;
import com.b2chat.ordermanagement.model.order.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

class OrderEventPublisherAdapterTest {

    @Test
    void shouldPublishOrderPlacedEventToSink() {
        Sinks.Many<OrderPlacedEvent> sink = Sinks.many().multicast().<OrderPlacedEvent>onBackpressureBuffer();
        Sinks.Many<OrderCompletedEvent> completedSink = Sinks.many().multicast()
                .<OrderCompletedEvent>onBackpressureBuffer();
        var adapter = new OrderEventPublisherAdapter(sink, completedSink);
        var event = new OrderPlacedEvent(new UUID(1L, 1L), new UUID(2L, 2L), Instant.parse("2026-08-16T20:00:00Z"));

        StepVerifier.create(sink.asFlux().take(1))
                .then(() -> adapter.publishOrderPlaced(event))
                .expectNext(event)
                .verifyComplete();
    }

    @Test
    void shouldPublishOrderCompletedEventToSink() {
        Sinks.Many<OrderPlacedEvent> placedSink = Sinks.many().multicast().<OrderPlacedEvent>onBackpressureBuffer();
        Sinks.Many<OrderCompletedEvent> completedSink = Sinks.many().multicast()
                .<OrderCompletedEvent>onBackpressureBuffer();
        var adapter = new OrderEventPublisherAdapter(placedSink, completedSink);
        var event = new OrderCompletedEvent(new UUID(1L, 1L), new UUID(2L, 2L),
                Instant.parse("2026-08-16T20:00:00Z"));

        StepVerifier.create(completedSink.asFlux().take(1))
                .then(() -> adapter.publishOrderCompleted(event))
                .expectNext(event)
                .verifyComplete();
    }
}
