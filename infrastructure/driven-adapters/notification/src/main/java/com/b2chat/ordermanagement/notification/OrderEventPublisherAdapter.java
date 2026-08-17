package com.b2chat.ordermanagement.notification;

import com.b2chat.ordermanagement.model.order.OrderPlacedEvent;
import com.b2chat.ordermanagement.model.order.gateways.OrderEventPublisher;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

@Component
public class OrderEventPublisherAdapter implements OrderEventPublisher {
    private static final Log LOG = LogFactory.getLog(OrderEventPublisherAdapter.class);
    private final Sinks.Many<OrderPlacedEvent> orderPlacedSink;

    public OrderEventPublisherAdapter(Sinks.Many<OrderPlacedEvent> orderPlacedSink) {
        this.orderPlacedSink = orderPlacedSink;
    }

    @Override
    public void publishOrderPlaced(OrderPlacedEvent event) {
        var result = orderPlacedSink.tryEmitNext(event);
        if (result.isFailure()) {
            LOG.warn("order_placed_event_emit_failed orderId=" + event.orderId()
                    + " userId=" + event.userId() + " result=" + result);
        }
    }
}
