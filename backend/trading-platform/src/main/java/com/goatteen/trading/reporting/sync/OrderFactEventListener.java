
package com.goatteen.trading.reporting.sync;

import com.goatteen.trading.reporting.event.OrderStatusChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderFactEventListener {

    private static final Logger logger =
            LoggerFactory.getLogger(OrderFactEventListener.class);

    private final OrderFactSyncService orderFactSyncService;

    public OrderFactEventListener(
            OrderFactSyncService orderFactSyncService) {
        this.orderFactSyncService = orderFactSyncService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {

        try {
            orderFactSyncService.syncOrderById(event.orderId());
        } catch (Exception ex) {
            logger.error(
                    "Failed to synchronize reporting for order {}",
                    event.orderId(),
                    ex);
        }
    }
}