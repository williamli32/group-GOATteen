package com.goatteen.trading.operations;

import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OperationsOrderSearchService {

    private final OrderRepository orderRepository;

    public OperationsOrderSearchService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<OperationsOrderResponse> search(
            Long orderId,
            String clientFirstName,
            String clientLastName,
            Long accountId,
            String instrumentSymbol,
            OrderSide side,
            OrderStatus status,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        return orderRepository.searchForOperations(
                        orderId,
                        clientFirstName,
                        clientLastName,
                        accountId,
                        instrumentSymbol,
                        side == null ? null : side.name(),
                        status == null ? null : status.name(),
                        fromDate,
                        toDate)
                .stream()
                .map(row -> new OperationsOrderResponse(
                        (Long) row[0],
                        (Long) row[2],
                        (String) row[4],
                        (Long) row[1],
                        (String) row[3],
                        (Long) row[5],
                        (String) row[6],
                        OrderSide.valueOf(String.valueOf(row[7])),
                        OrderStatus.valueOf(String.valueOf(row[8])),
                        (java.math.BigDecimal) row[9],
                        toLocalDateTime(row[10])))
                .toList();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        return LocalDateTime.parse(value.toString());
    }
}
