package com.goatteen.trading.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AddFundsResponse(
        Long accountId,
        Long transactionId,
        BigDecimal amount,
        String currency,
        BigDecimal cashBalance,
        LocalDateTime createdAt,
        boolean replayed) {
}
