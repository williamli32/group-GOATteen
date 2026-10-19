package com.goatteen.trading.marketdata;

import com.goatteen.trading.marketdata.dto.DailyPriceChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DailyPriceChangeTest {
    @ParameterizedTest
    @CsvSource({"100,110,10.00", "100,90,-10.00", "100,100,0.00",
            "0.011,0.011022,0.20", "100,100.004,0.00", "100,100.005,0.01", "100,99.995,-0.01"})
    void calculatesSignedPercentWithoutLosingSmallPricePrecision(String reference, String current, String expected) {
        var day = LocalDate.of(2026, 10, 19);
        var time = Instant.parse("2026-10-19T00:00:01Z");
        var result = DailyPriceChange.calculate(new BigDecimal(current), new BigDecimal(reference), day, time);
        assertEquals(new BigDecimal(expected), result.percent());
        assertEquals(day, result.tradingDay());
        assertEquals(time, result.referenceTime());
    }

    @Test
    void missingOrInvalidPricesDoNotProduceAnInventedZeroOrInfinity() {
        var day = LocalDate.of(2026, 10, 19);
        var now = Instant.parse("2026-10-19T00:00:01Z");
        assertNull(DailyPriceChange.calculate(null, BigDecimal.ONE, day, now));
        assertNull(DailyPriceChange.calculate(BigDecimal.ONE, null, day, now));
        assertNull(DailyPriceChange.calculate(BigDecimal.ONE, BigDecimal.ZERO, day, now));
        assertNull(DailyPriceChange.calculate(BigDecimal.ZERO, BigDecimal.ONE, day, now));
    }
}
