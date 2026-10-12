package com.goatteen.trading.reporting;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.client.Client;
import com.goatteen.trading.client.ClientRepository;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentClass;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.reporting.data.OrderFact;
import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.sync.OrderFactBackfillService;
import com.goatteen.trading.user.User;
import com.goatteen.trading.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderFactBackfillIntegrationTest {

    @Autowired
    private OrderFactBackfillService backfillService;

    @Autowired
    private OrderFactRepository orderFactRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void backfillRestoresHistoricalOrderWithoutDuplicates() {

        // Create unique historical data in the test database.
        String unique = UUID.randomUUID().toString();

        User user = userRepository.save(
                new User("backfill-" + unique + "@example.com", "hashed-password")
        );

        Client client = clientRepository.save(
                new Client(user, "Backfill", "Client")
        );

        Account account = accountRepository.save(
                new Account(client, "BACKFILL-" + unique, "USD")
        );

        Instrument instrument = instrumentRepository.save(
                new Instrument(
                        "B" + unique.replace("-", "").substring(0, 7),
                        "Backfill Instrument",
                        InstrumentClass.EQUITY,
                        "NASDAQ",
                        "US",
                        "USD"
                )
        );

        Order order = new Order();
        order.setAccount(account);
        order.setInstrument(instrument);
        order.setSide(OrderSide.BUY);
        order.setQuantity(new BigDecimal("10.00"));
        order.setStatus(OrderStatus.SUBMITTED);
        order.setSubmittedAt(LocalDateTime.now().minusDays(10));

        Order savedOrder = orderRepository.saveAndFlush(order);

        // Repository-created order has not gone through the execution pipeline.
        assertTrue(orderFactRepository.findByOrderId(savedOrder.getId()).isEmpty());

        // First backfill must create the reporting record.
        backfillService.backfill();

        OrderFact fact = orderFactRepository.findByOrderId(savedOrder.getId())
                .orElseThrow(() ->
                        new AssertionError("Backfill did not create OrderFact"));

        assertEquals(savedOrder.getId(), fact.getOrderId());
        assertEquals("SUBMITTED", fact.getFinalStatus());
        assertEquals(account.getId(), fact.getAccountId());
        assertEquals(client.getId(), fact.getClientId());

        long countAfterFirstRun = orderFactRepository.count();

        // Running backfill again must not create duplicate records.
        backfillService.backfill();

        assertEquals(countAfterFirstRun, orderFactRepository.count());
        assertTrue(orderFactRepository.findByOrderId(savedOrder.getId()).isPresent());
    }
}