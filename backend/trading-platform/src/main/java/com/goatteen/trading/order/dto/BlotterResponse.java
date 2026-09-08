package com.goatteen.trading.order.dto;

import java.util.List;


public class BlotterResponse {

    private Long accountId;
    private List<OrderResponse> orders;

    public BlotterResponse() {
    }

    public BlotterResponse(Long accountId, List<OrderResponse> orders) {
        this.accountId = accountId;
        this.orders = orders;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public List<OrderResponse> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderResponse> orders) {
        this.orders = orders;
    }
}
