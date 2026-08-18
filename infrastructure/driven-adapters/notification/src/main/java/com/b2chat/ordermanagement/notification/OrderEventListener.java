package com.b2chat.ordermanagement.notification;

import com.b2chat.ordermanagement.model.order.OrderCompletedEvent;
import com.b2chat.ordermanagement.model.order.OrderPlacedEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

@Component
public class OrderEventListener implements InitializingBean {
    private static final Log LOG = LogFactory.getLog(OrderEventListener.class);
    private final Sinks.Many<OrderPlacedEvent> orderPlacedSink;
    private final Sinks.Many<OrderCompletedEvent> orderCompletedSink;

    public OrderEventListener(Sinks.Many<OrderPlacedEvent> orderPlacedSink,
                              Sinks.Many<OrderCompletedEvent> orderCompletedSink) {
        this.orderPlacedSink = orderPlacedSink;
        this.orderCompletedSink = orderCompletedSink;
    }

    @Override
    public void afterPropertiesSet() {
        orderPlacedSink.asFlux()
                .subscribe(this::notifyOrderReceived,
                        error -> LOG.error("order_placed_listener_failed", error));

        orderCompletedSink.asFlux()
                .subscribe(this::notifyOrderCompleted,
                        error -> LOG.error("order_completed_listener_failed", error));
    }

    private void notifyOrderReceived(OrderPlacedEvent event) {
        LOG.info("order_received_notification_sent orderId=" + event.orderId()
                + " userId=" + event.userId() + " occurredAt=" + event.occurredAt());
    }

    private void notifyOrderCompleted(OrderCompletedEvent event) {
        LOG.info("order_completed_notification_sent orderId=" + event.orderId()
                + " userId=" + event.userId() + " occurredAt=" + event.occurredAt());
    }
}
