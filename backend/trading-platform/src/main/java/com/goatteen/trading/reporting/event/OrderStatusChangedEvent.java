
package com.goatteen.trading.reporting.event;

/**
 * Event published whenever an order changes status.
 * Only the order ID is passed.
 */
public record OrderStatusChangedEvent(Long orderId) {
}
