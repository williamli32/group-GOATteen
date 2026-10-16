package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.metrics.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BusinessInsightsCalculatorTest {
    private final ReportingWindow window = new ReportingWindow(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16));

    @Test
    void comparisonHasEqualLengthAcrossMonthYearAndLeapBoundaries() {
        assertEquals(new ReportingWindow(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 30)),
                window.previous());
        var january = new ReportingWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
        assertEquals(LocalDate.of(2025, 12, 29), january.previous().fromDate());
        var march = new ReportingWindow(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 2));
        assertEquals(LocalDate.of(2024, 2, 28), march.previous().fromDate());
        assertEquals(january.fromInclusive(), january.previous().toExclusive());
        assertEquals(window.days(), window.previous().days());
    }

    @Test
    void singleMonthUsesActualBaselineAndZeroBaselineIsNotInvented() {
        var insight = calculate(metrics(5, 0, 0), metrics(2, 0, 0), List.of(), List.of(), List.of());
        assertEquals(5, insight.currentFillCount());
        assertEquals(2, insight.previousFillCount());
        assertEquals(0, new BigDecimal("150").compareTo(insight.volumeChangePercent()));
        assertNull(BusinessInsightsCalculator.percentChange(5, 0));
        assertEquals(0, BusinessInsightsCalculator.percentChange(0, 0).signum());
        assertEquals(0, new BigDecimal("-100").compareTo(BusinessInsightsCalculator.percentChange(0, 5)));
    }

    @Test
    void ranksByFillCountAndSeparatesNewActivityFromComparableGrowth() {
        var current = List.of(instrument(3, "NEW", 20), instrument(2, "B", 6), instrument(1, "A", 6));
        var previous = List.of(instrument(2, "B", 3), instrument(1, "A", 2));
        var insight = calculate(metrics(32, 0, 0), metrics(5, 0, 0), current, previous, List.of());
        assertEquals("NEW", insight.mostActiveInstrument().symbol());
        assertEquals("A", insight.fastestGrowingInstrument().symbol());
        assertEquals(0, new BigDecimal("200").compareTo(insight.fastestGrowingInstrument().changePercent()));
        assertEquals(1, insight.newlyActiveInstruments().size());
        assertNull(insight.newlyActiveInstruments().get(0).changePercent());
    }

    @Test
    void computesBuySellPercentagesPeakDayAndPercentagePointChanges() {
        var days = List.of(
                day("2026-10-01", 6, 0, 3, 1),
                day("2026-10-02", 6, 2, 3, 1),
                day("2026-10-03", 0, 0, 0, 0));
        var insight = calculate(metrics(6, 2, 2), metrics(2, 2, 1), List.of(), List.of(), days);
        assertEquals(0, new BigDecimal("75").compareTo(insight.buySharePercent()));
        assertEquals(0, new BigDecimal("25").compareTo(insight.sellSharePercent()));
        assertEquals(LocalDate.of(2026, 10, 1), insight.highestVolumeDay().date());
        // Current 2/10 = 20%; baseline 1/5 = 20%: zero percentage points.
        assertEquals(0, insight.rejectionRateChangePercentagePoints().signum());
        assertNull(insight.rejectionTrend().get(2).rejectionRate());
        assertEquals(0, new BigDecimal("33.33").compareTo(insight.rejectionTrend().get(1).rejectionRate()));
    }

    @Test
    void emptyAndDecliningPeriodsHaveHonestEmptyStates() {
        var empty = calculate(metrics(0, 0, 0), metrics(0, 0, 0), List.of(), List.of(), List.of());
        assertNull(empty.mostActiveInstrument());
        assertNull(empty.fastestGrowingInstrument());
        assertNull(empty.highestVolumeDay());
        assertNull(empty.buySharePercent());
        assertNull(empty.rejectionRateChangePercentagePoints());
        var decline = calculate(metrics(1, 0, 0), metrics(2, 0, 0),
                List.of(instrument(1, "A", 1)), List.of(instrument(1, "A", 2)), List.of());
        assertNull(decline.fastestGrowingInstrument());
    }

    private BusinessInsights calculate(TradingActivityMetrics current, TradingActivityMetrics previous,
            List<InstrumentMetrics> instruments, List<InstrumentMetrics> old, List<ActivityTrendPoint> days) {
        return BusinessInsightsCalculator.calculate(window, "USD", current, previous, instruments, old, days);
    }

    private static InstrumentMetrics instrument(long id, String symbol, long fills) {
        return new InstrumentMetrics(id, symbol, "USD", fills, BigDecimal.valueOf(fills), BigDecimal.ZERO);
    }

    private static ActivityTrendPoint day(String date, long orders, long rejected, long buys, long sells) {
        return new ActivityTrendPoint(LocalDate.parse(date), orders, buys + sells, rejected, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, buys, sells);
    }

    private static TradingActivityMetrics metrics(long buys, long sells, long rejected) {
        long orders = buys + sells + rejected;
        BigDecimal rate = orders == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(rejected * 100)
                .divide(BigDecimal.valueOf(orders), 2, java.math.RoundingMode.HALF_UP);
        return new TradingActivityMetrics(orders, buys + sells, rejected, 0,
                BigDecimal.ZERO, rate, BigDecimal.ZERO, BigDecimal.ZERO,
                buys, BigDecimal.ZERO, BigDecimal.ZERO, sells, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
