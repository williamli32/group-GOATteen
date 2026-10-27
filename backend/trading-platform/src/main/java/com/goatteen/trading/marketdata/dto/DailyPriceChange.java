package com.goatteen.trading.marketdata.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

public record DailyPriceChange(
        LocalDate tradingDay,
        BigDecimal referencePrice,
        Instant referenceTime,
        BigDecimal percent) {

    public static DailyPriceChange calculate(
            BigDecimal lastPrice, BigDecimal referencePrice, LocalDate day, Instant referenceTime) {
        if (lastPrice == null || referencePrice == null
                || lastPrice.signum() <= 0 || referencePrice.signum() <= 0) {
            return null;
        }
        BigDecimal percent = lastPrice.subtract(referencePrice).multiply(new BigDecimal("100"))
                .divide(referencePrice, 2, RoundingMode.HALF_UP);
        return new DailyPriceChange(day, referencePrice, referenceTime, percent);
    }
}
