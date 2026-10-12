
package com.goatteen.trading.reporting.sync;

import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class OrderFactBackfillService {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderFactSyncService orderFactSyncService;

    public OrderFactBackfillService(
            OrderRepository orderRepository,
            OrderFactSyncService orderFactSyncService) {
        this.orderRepository = orderRepository;
        this.orderFactSyncService = orderFactSyncService;
    }

    /**
     * Copy historical orders into the reporting dataset.
     *
     * This method is deliberately not run automatically
     * when the application starts.
     */
    public long backfill() {

        long synchronizedOrders = 0;
        int pageNumber = 0;

        Page<Order> page;

        do {
            page = orderRepository.findAll(
                    PageRequest.of(
                            pageNumber,
                            BATCH_SIZE,
                            Sort.by(Sort.Direction.ASC, "id")
                    )
            );

            for (Order order : page.getContent()) {
                orderFactSyncService.syncOrderById(order.getId());
                synchronizedOrders++;
            }

            pageNumber++;

        } while (page.hasNext());

        return synchronizedOrders;
    }
}
