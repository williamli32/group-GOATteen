package com.goatteen.trading.alerts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertEvaluatorTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void noQualifyingQuoteDoesNotWrite() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(LocalDateTime.class)))
            .thenReturn(List.of());
        new PriceAlertEvaluator(jdbc, 30).evaluate();
        verify(jdbc).query(anyString(), any(RowMapper.class), any(LocalDateTime.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void triggerThenResetThenTriggerAgainGeneratesTwoDifferentNotifications() throws Exception {
        // Simulate the database's eligible transition rows. Each scheduled poll
        // only selects an alert when its latest quote changes its state.
        AtomicInteger poll = new AtomicInteger();
        when(jdbc.query(anyString(), any(RowMapper.class), any(LocalDateTime.class)))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(0);
                assertTrue(query.contains("a.enabled=TRUE"));
                assertTrue(query.contains("FOR UPDATE OF a SKIP LOCKED"));
                assertTrue(query.contains("a.armed"));
                assertTrue(query.contains("q.last_price < a.threshold"));
                assertTrue(query.contains("q.last_price > a.threshold"));
                int step = poll.getAndIncrement();
                if (step == 1) return List.of(); // same price above threshold: no duplicate
                return List.of(row((RowMapper<Object>) invocation.getArgument(1),
                    step != 2, step == 3 ? 2 : 1, step == 2 ? "190" : "210"));
            });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PriceAlertEvaluator evaluator = new PriceAlertEvaluator(jdbc, 30);
        evaluator.evaluate(); // 210 > 200 -> notification #1, armed=false
        evaluator.evaluate(); // still 210 -> no notification
        evaluator.evaluate(); // 190 < 200 -> armed=true, generation=2
        evaluator.evaluate(); // 210 > 200 -> notification #2

        // Two notifications, one rearm and two disarms = 5 updates.
        // The SQL explicitly ensures a separate generation per trigger.
        verify(jdbc, times(5)).update(anyString(), any(Object[].class));
        ArgumentCaptor<Object[]> notificationArguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(
            contains("INSERT INTO price_alert_notifications"), notificationArguments.capture());
        List<Object[]> notifications = notificationArguments.getAllValues();
        assertEquals(List.of(1, 2), notifications.stream().map(arguments -> arguments[2]).toList());
        assertTrue(notifications.stream().allMatch(arguments -> Long.valueOf(10L).equals(arguments[0])));
        assertEquals(4, poll.get());
    }

    private Object row(RowMapper<Object> mapper, boolean armed, int generation,
                       String observedPrice) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(10L);
        when(rs.getLong(2)).thenReturn(21L);
        when(rs.getInt(3)).thenReturn(generation);
        when(rs.getLong(4)).thenReturn(30L);
        when(rs.getString(5)).thenReturn("ABOVE");
        when(rs.getBigDecimal(6)).thenReturn(new BigDecimal("200.00"));
        when(rs.getBoolean(7)).thenReturn(armed);
        when(rs.getString(8)).thenReturn("AAPL");
        when(rs.getString(9)).thenReturn("USD");
        when(rs.getLong(10)).thenReturn(50L);
        when(rs.getBigDecimal(11)).thenReturn(new BigDecimal(observedPrice));
        when(rs.getTimestamp(12)).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
        return mapper.mapRow(rs, 0);
    }
}
