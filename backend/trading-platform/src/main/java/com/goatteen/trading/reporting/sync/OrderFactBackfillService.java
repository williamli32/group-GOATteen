
package com.goatteen.trading.reporting.sync;

import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderFactBackfillService {
        private static final Logger logger =
            LoggerFactory.getLogger(OrderFactBackfillService.class);

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
        List<Long> failedOrderIds = new ArrayList<>();

        Page<Order> page;

        do {
            page = orderRepository.findAll(
                    PageRequest.of(
                            pageNumber,
                            BATCH_SIZE,
                            Sort.by(Sort.Direction.ASC, "id")));

            for (Order order : page.getContent()) {
                try {
                    orderFactSyncService.syncOrderById(order.getId());
                    synchronizedOrders++;
                } catch (Exception ex) {
                    failedOrderIds.add(order.getId());
                    logger.error(
                            "Reporting backfill failed for order {}",
                            order.getId(),
                            ex);
                }
            }

            pageNumber++;

        } while (page.hasNext());

        if (!failedOrderIds.isEmpty()) {
            throw new IllegalStateException(
                    "Reporting backfill incomplete. Failed order IDs: "
                            + failedOrderIds
                            + ". Successfully synchronized: "
                            + synchronizedOrders);
        }

        return synchronizedOrders;
    }
}
