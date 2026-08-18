package com.b2chat.ordermanagement.notification;

import com.b2chat.ordermanagement.model.order.OrderCompletedEvent;
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
    private final Sinks.Many<OrderCompletedEvent> orderCompletedSink;

    public OrderEventPublisherAdapter(Sinks.Many<OrderPlacedEvent> orderPlacedSink,
                                      Sinks.Many<OrderCompletedEvent> orderCompletedSink) {
        this.orderPlacedSink = orderPlacedSink;
        this.orderCompletedSink = orderCompletedSink;
    }

    @Override
    public void publishOrderPlaced(OrderPlacedEvent event) {
        var result = orderPlacedSink.tryEmitNext(event);
        if (result.isFailure()) {
            LOG.warn("order_placed_event_emit_failed orderId=" + event.orderId()
                    + " userId=" + event.userId() + " result=" + result);
        }
    }

    @Override
    public void publishOrderCompleted(OrderCompletedEvent event) {
        var result = orderCompletedSink.tryEmitNext(event);
        if (result.isFailure()) {
            LOG.warn("order_completed_event_emit_failed orderId=" + event.orderId()
                    + " userId=" + event.userId() + " result=" + result);
        }
    }
}
