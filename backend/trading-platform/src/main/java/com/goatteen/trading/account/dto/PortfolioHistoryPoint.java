package com.goatteen.trading.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioHistoryPoint(
        LocalDate date,
        BigDecimal value,
        BigDecimal cashValue,
        BigDecimal holdingsValue) {
}
