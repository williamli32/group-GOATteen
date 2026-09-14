package com.goatteen.trading.account.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long accountId,
        String accountNumber,
        BigDecimal cashBalance,
        String currency,
        String firstName,
        String lastName
) {}