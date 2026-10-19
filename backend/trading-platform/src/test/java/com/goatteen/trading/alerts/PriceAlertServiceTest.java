package com.goatteen.trading.alerts;

import com.goatteen.trading.auth.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock CurrentUserService currentUser;

    @Test void rejectsZeroThresholdWithoutWriting() {
        var service = new PriceAlertService(jdbc,currentUser);
        assertThrows(ResponseStatusException.class, () -> service.create(
            new PriceAlertService.Change(1,"ABOVE",BigDecimal.ZERO,true)));
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsUnknownDirectionWithoutWriting() {
        var service = new PriceAlertService(jdbc,currentUser);
        assertThrows(ResponseStatusException.class, () -> service.create(
            new PriceAlertService.Change(1,"CROSS",new BigDecimal("15"),true)));
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsExcessivePrecision() {
        var service = new PriceAlertService(jdbc,currentUser);
        assertThrows(ResponseStatusException.class, () -> service.create(
            new PriceAlertService.Change(1,"BELOW",new BigDecimal("0.000000001"),true)));
        verifyNoInteractions(jdbc);
    }
    @Test void clearingOneNotificationIsScopedToTheSignedInUser() {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(jdbc.update(anyString(), eq(24L), eq(7L))).thenReturn(1);
        var service = new PriceAlertService(jdbc, currentUser);
        service.clearNotification(24L);
        service.clearNotification(24L); // repeated DELETE is idempotent
        verify(jdbc, times(2)).update(argThat(sql ->
            sql.contains("SET cleared_at=COALESCE(cleared_at,CURRENT_TIMESTAMP)") &&
            sql.contains("WHERE id=? AND user_id=?")), eq(24L), eq(7L));
    }

    @Test void clearingAnotherUsersNotificationFails() {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        var service = new PriceAlertService(jdbc, currentUser);
        assertThrows(ResponseStatusException.class, () -> service.clearNotification(99L));
        verify(jdbc).update(argThat(sql -> sql.contains("WHERE id=? AND user_id=?")),
            eq(99L), eq(7L));
    }

    @Test void clearAllOnlyAffectsTheSignedInUsersVisibleNotifications() {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        var service = new PriceAlertService(jdbc, currentUser);
        service.clearAllNotifications();
        verify(jdbc).update(argThat(sql ->
            sql.contains("UPDATE price_alert_notifications") &&
            sql.contains("WHERE user_id=? AND cleared_at IS NULL")), eq(7L));
    }

    @Test void listingNotificationsExcludesClearedHistory() {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        var service = new PriceAlertService(jdbc, currentUser);
        service.notifications();
        verify(jdbc).query(argThat(sql ->
            sql.contains("cleared_at IS NULL") && sql.contains("user_id=?")),
            any(org.springframework.jdbc.core.RowMapper.class), eq(7L));
    }

    @Test void markingReadIsScopedToAuthenticatedUserAndIdempotent() {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(jdbc.update(anyString(), eq(24L), eq(7L))).thenReturn(1);
        var service = new PriceAlertService(jdbc, currentUser);
        service.markRead(24L);
        service.markRead(24L);
        verify(jdbc, times(2)).update(argThat(sql ->
            sql.contains("COALESCE(read_at,CURRENT_TIMESTAMP)") &&
            sql.contains("id=? AND user_id=?")), eq(24L), eq(7L));
    }

    @Test void markingAnotherUsersNotificationFailsSafely() {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        var service = new PriceAlertService(jdbc, currentUser);
        assertThrows(ResponseStatusException.class, () -> service.markRead(99L));
    }
}
