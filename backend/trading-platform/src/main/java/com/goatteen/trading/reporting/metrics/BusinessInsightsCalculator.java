package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure calculation engine: no transactional repositories or mutable application state. */
public final class BusinessInsightsCalculator {
    private BusinessInsightsCalculator() {
    }

    public static BusinessInsights calculate(
            ReportingWindow window,
            String currency,
            TradingActivityMetrics current,
            TradingActivityMetrics previous,
            List<InstrumentMetrics> currentInstruments,
            List<InstrumentMetrics> previousInstruments,
            List<ActivityTrendPoint> trends) {

        Map<Long, InstrumentMetrics> baseline = previousInstruments.stream()
                .collect(Collectors.toMap(InstrumentMetrics::instrumentId, Function.identity()));
        List<BusinessInsights.InstrumentInsight> instruments = currentInstruments.stream()
                .map(instrument -> {
                    InstrumentMetrics old = baseline.get(instrument.instrumentId());
                    long previousCount = old == null ? 0 : old.fillCount();
                    return new BusinessInsights.InstrumentInsight(
                            instrument.instrumentId(), instrument.instrumentSymbol(), instrument.fillCount(),
                            previousCount, percentChange(instrument.fillCount(), previousCount),
                            previousCount == 0 && instrument.fillCount() > 0);
                })
                .toList();

        Comparator<BusinessInsights.InstrumentInsight> byActivity =
                Comparator.comparingLong(BusinessInsights.InstrumentInsight::fillCount).reversed()
                        .thenComparing(BusinessInsights.InstrumentInsight::instrumentId);
        var mostActive = instruments.stream()
                .filter(instrument -> instrument.fillCount() > 0)
                .sorted(byActivity).findFirst().orElse(null);
        var fastestGrowing = instruments.stream()
                .filter(instrument -> instrument.previousFillCount() > 0
                        && instrument.fillCount() > instrument.previousFillCount())
                .sorted(Comparator.comparing(BusinessInsights.InstrumentInsight::changePercent).reversed()
                        .thenComparing(byActivity))
                .findFirst().orElse(null);
        var newlyActive = instruments.stream()
                .filter(BusinessInsights.InstrumentInsight::newlyActive)
                .sorted(byActivity).toList();
        var peakDay = trends.stream()
                .filter(point -> point.buyFills() + point.sellFills() > 0)
                .sorted(Comparator.<ActivityTrendPoint>comparingLong(
                        point -> point.buyFills() + point.sellFills()).reversed()
                        .thenComparing(ActivityTrendPoint::date))
                .map(point -> new BusinessInsights.PeakDay(point.date(), point.buyFills() + point.sellFills()))
                .findFirst().orElse(null);
        var rejectionTrend = trends.stream()
                .map(point -> new BusinessInsights.RejectionTrendPoint(
                        point.date(), point.totalOrders(), point.rejectedOrders(),
                        point.totalOrders() == 0 ? null : percent(point.rejectedOrders(), point.totalOrders())))
                .toList();

        long currentFills = current.buyFills() + current.sellFills();
        long previousFills = previous.buyFills() + previous.sellFills();
        BigDecimal rejectionChange = current.totalOrders() == 0 || previous.totalOrders() == 0
                ? null : current.rejectionRate().subtract(previous.rejectionRate());

        return new BusinessInsights(
                window, window.previous(), currency, current, previous,
                currentFills, previousFills, percentChange(currentFills, previousFills),
                currentFills == 0 ? null : percent(current.buyFills(), currentFills),
                currentFills == 0 ? null : percent(current.sellFills(), currentFills),
                mostActive, fastestGrowing, newlyActive, peakDay, rejectionChange, rejectionTrend);
    }

    public static BigDecimal percentChange(long current, long previous) {
        return percentChange(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    /** null means an undefined percentage/new activity; two empty periods are unchanged. */
    public static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() == 0 ? BigDecimal.ZERO.setScale(2) : null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(long count, long total) {
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
