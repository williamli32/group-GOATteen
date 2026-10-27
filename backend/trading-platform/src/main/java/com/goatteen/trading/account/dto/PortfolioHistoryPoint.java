package com.goatteen.trading.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioHistoryPoint(
        LocalDate date,
        BigDecimal value,
        BigDecimal cashValue,
        BigDecimal holdingsValue,
        List<String> unavailable) {
}
