package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.client.Client;
import com.goatteen.trading.client.ClientRepository;
import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.data.TradeFactRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClientMetricsService {

        private final OrderFactRepository orderFactRepository;

        private final TradeFactRepository tradeFactRepository;

        private final ClientRepository clientRepository;

        @Autowired
        public ClientMetricsService(
                        OrderFactRepository orderFactRepository,
                        TradeFactRepository tradeFactRepository,
                        ClientRepository clientRepository) {

                this.orderFactRepository = orderFactRepository;

                this.tradeFactRepository = tradeFactRepository;

                this.clientRepository = clientRepository;
        }

        public ClientMetricsService(
                        OrderFactRepository orderFactRepository,
                        TradeFactRepository tradeFactRepository) {

                this(
                                orderFactRepository,
                                tradeFactRepository,
                                null);
        }

        @Transactional(readOnly = true)
        public List<ClientMetrics> calculate(
                        LocalDateTime startDate,
                        LocalDateTime endDate) {

                return calculate(
                                ReportingFilters.forPeriod(
                                                startDate,
                                                endDate));
        }

        @Transactional(readOnly = true)
        public List<ClientMetrics> calculate(
                        ReportingFilters filters) {

                Map<ClientCurrencyKey, MutableClientMetrics> metrics = new HashMap<>();

                /*
                 * All orders,
                 * including rejected orders.
                 */
                for (Object[] row : orderFactRepository
                                .summarizeOrdersByClient(
                                                filters.fromDate(),
                                                filters.toDate(),
                                                filters.instrumentId(),
                                                filters.side(),
                                                filters.status(),
                                                filters.clientId(),
                                                filters.accountId(),
                                                filters.currency())) {

                        Long clientId = ((Number) row[0])
                                        .longValue();

                        String currency = (String) row[1];

                        ClientCurrencyKey key = new ClientCurrencyKey(
                                        clientId,
                                        currency);

                        MutableClientMetrics value = metrics.computeIfAbsent(
                                        key,
                                        ignored -> new MutableClientMetrics());

                        value.orderCount = ((Number) row[2])
                                        .longValue();
                }

                /*
                 * Successful executions only.
                 */
                for (Object[] row : tradeFactRepository
                                .summarizeByClient(
                                                filters.fromDate(),
                                                filters.toDate(),
                                                filters.instrumentId(),
                                                filters.side(),
                                                filters.status(),
                                                filters.clientId(),
                                                filters.accountId(),
                                                filters.currency())) {

                        Long clientId = ((Number) row[0])
                                        .longValue();

                        String currency = (String) row[1];

                        ClientCurrencyKey key = new ClientCurrencyKey(
                                        clientId,
                                        currency);

                        MutableClientMetrics value = metrics.computeIfAbsent(
                                        key,
                                        ignored -> new MutableClientMetrics());

                        value.fillCount = ((Number) row[2])
                                        .longValue();

                        value.executedQuantity = decimal(
                                        row[3]);

                        value.tradedValue = decimal(
                                        row[4]);
                }

                Map<Long, String> clientNames = clientRepository == null

                                ? Map.of()

                                : clientRepository
                                                .findAllById(
                                                                metrics
                                                                                .keySet()
                                                                                .stream()
                                                                                .map(
                                                                                                ClientCurrencyKey::clientId)
                                                                                .distinct()
                                                                                .toList())
                                                .stream()
                                                .collect(
                                                                java.util.stream.Collectors
                                                                                .toMap(
                                                                                                Client::getId,
                                                                                                client -> client.getFirstName()
                                                                                                                + " "
                                                                                                                + client.getLastName()));

                return metrics
                                .entrySet()
                                .stream()
                                .map(
                                                entry -> {

                                                        ClientCurrencyKey key = entry.getKey();

                                                        MutableClientMetrics value = entry.getValue();

                                                        return new ClientMetrics(
                                                                        key.clientId(),

                                                                        clientNames
                                                                                        .getOrDefault(
                                                                                                        key.clientId(),
                                                                                                        "Client "
                                                                                                                        + key.clientId()),

                                                                        key.currency(),

                                                                        value.orderCount,

                                                                        value.fillCount,

                                                                        value.executedQuantity,

                                                                        value.tradedValue);
                                                })
                                .sorted(
                                                Comparator
                                                                .comparingLong(
                                                                                ClientMetrics::orderCount)
                                                                .reversed()
                                                                .thenComparing(
                                                                                ClientMetrics::clientId))
                                .toList();
        }

        private BigDecimal decimal(
                        Object value) {

                if (value == null) {

                        return BigDecimal.ZERO;
                }

                if (value instanceof BigDecimal bigDecimal) {

                        return bigDecimal;
                }

                return new BigDecimal(
                                value.toString());
        }

        private record ClientCurrencyKey(
                        Long clientId,
                        String currency) {
        }

        private static class MutableClientMetrics {

                private long orderCount;

                private long fillCount;

                private BigDecimal executedQuantity = BigDecimal.ZERO;

                private BigDecimal tradedValue = BigDecimal.ZERO;
        }
}