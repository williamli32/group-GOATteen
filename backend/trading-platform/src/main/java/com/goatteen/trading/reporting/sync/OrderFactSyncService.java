
package com.goatteen.trading.reporting.sync;

import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.reporting.data.OrderFact;
import com.goatteen.trading.reporting.data.OrderFactRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderFactSyncService {

    private final OrderFactRepository orderFactRepository;
    private final OrderRepository orderRepository;

    public OrderFactSyncService(
            OrderFactRepository orderFactRepository,
            OrderRepository orderRepository) {
        this.orderFactRepository = orderFactRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncOrderById(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found: " + orderId));

        syncOrder(order);
    }

    public void syncOrder(Order order) {

        OrderFact fact = orderFactRepository
                .findById(order.getId())
                .orElseGet(() -> new OrderFact(
                        order.getId(),
                        order.getAccount().getId(),
                        order.getAccount().getClient().getId(),
                        order.getInstrument().getId(),
                        order.getInstrument().getSymbol(),
                        order.getInstrument().getCurrency(),
                        order.getSide().name(),
                        order.getQuantity(),
                        order.getStatus().name(),
                        order.getSubmittedAt(),
                        order.getCompletedAt(),
                        order.getRejectionReason()
                ));

        fact.updateStatus(
                order.getStatus().name(),
                order.getCompletedAt(),
                order.getRejectionReason()
        );

        orderFactRepository.save(fact);
    }
}
