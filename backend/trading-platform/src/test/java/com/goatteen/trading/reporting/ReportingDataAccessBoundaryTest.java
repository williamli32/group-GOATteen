package com.goatteen.trading.reporting;

import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.portfolio.CashTransactionRepository;
import com.goatteen.trading.portfolio.PositionRepository;
import com.goatteen.trading.reporting.controller.InternalReportingController;
import com.goatteen.trading.reporting.controller.ReportingController;
import com.goatteen.trading.reporting.controller.BusinessInsightsController;
import com.goatteen.trading.reporting.metrics.BusinessInsightsService;
import com.goatteen.trading.reporting.metrics.PeriodicReportingService;
import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.data.TradeFactRepository;
import com.goatteen.trading.reporting.metrics.ActivityTrendService;
import com.goatteen.trading.reporting.metrics.ClientMetricsService;
import com.goatteen.trading.reporting.metrics.InstrumentMetricsService;
import com.goatteen.trading.reporting.metrics.ReportingActivityService;
import com.goatteen.trading.reporting.metrics.TradingActivityMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportingDataAccessBoundaryTest {

    private static final List<Class<?>> REPORTING_COMPONENTS = List.of(
            TradingActivityMetricsService.class,
            ActivityTrendService.class,
            ClientMetricsService.class,
            InstrumentMetricsService.class,
            ReportingActivityService.class,
            BusinessInsightsService.class,
            PeriodicReportingService.class,
            BusinessInsightsController.class,
            ReportingController.class,
            InternalReportingController.class);

    private static final List<Class<?>> LIVE_EXECUTION_REPOSITORIES = List.of(
            OrderRepository.class,
            FillRepository.class,
            AccountRepository.class,
            PositionRepository.class,
            CashTransactionRepository.class);

    @Test
    void reportingComponentsDoNotDependOnLiveExecutionRepositories() {
        for (Class<?> component : REPORTING_COMPONENTS) {
            for (Field field : component.getDeclaredFields()) {
                for (Class<?> forbiddenRepository : LIVE_EXECUTION_REPOSITORIES) {
                    assertFalse(
                            forbiddenRepository.isAssignableFrom(field.getType()),
                            () -> component.getSimpleName()
                                    + " must not depend on live repository "
                                    + forbiddenRepository.getSimpleName());
                }
            }
        }
    }

    @Test
    void reportingMetricServicesDependOnReportingRepositories() {
        assertHasFieldOfType(TradingActivityMetricsService.class, OrderFactRepository.class);
        assertHasFieldOfType(TradingActivityMetricsService.class, TradeFactRepository.class);
        assertHasFieldOfType(ActivityTrendService.class, OrderFactRepository.class);
        assertHasFieldOfType(ActivityTrendService.class, TradeFactRepository.class);
        assertHasFieldOfType(ClientMetricsService.class, OrderFactRepository.class);
        assertHasFieldOfType(ClientMetricsService.class, TradeFactRepository.class);
        assertHasFieldOfType(InstrumentMetricsService.class, TradeFactRepository.class);
        assertHasFieldOfType(ReportingActivityService.class, OrderFactRepository.class);
    }

    @Test
    void reportingRepositoriesExposeNoModifyingQueries() {
        assertNoModifyingMethods(OrderFactRepository.class);
        assertNoModifyingMethods(TradeFactRepository.class);
    }

    private void assertHasFieldOfType(Class<?> component, Class<?> expectedType) {
        assertTrue(
                List.of(component.getDeclaredFields()).stream()
                        .anyMatch(field -> expectedType.isAssignableFrom(field.getType())),
                () -> component.getSimpleName()
                        + " must depend on "
                        + expectedType.getSimpleName());
    }

    private void assertNoModifyingMethods(Class<?> repository) {
        for (var method : repository.getDeclaredMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query != null) {
                String sql = query.value().stripLeading();
                assertTrue(sql.toUpperCase(java.util.Locale.ROOT).startsWith("SELECT"),
                        () -> "Reporting query must be SELECT-only: " + method.getName());
                assertFalse(java.util.regex.Pattern.compile(
                        "(?i)\\b(?:FROM|JOIN)\\s+(?:public\\.)?(?:orders|fills|accounts|positions|cash_transactions|position_history)\\b")
                        .matcher(sql).find(),
                        () -> "Analytical query touches a live trading table: " + method.getName());
            }
            assertFalse(
                    method.isAnnotationPresent(Modifying.class),
                    () -> repository.getSimpleName()
                            + " must not expose modifying query "
                            + method.getName());
        }
    }
}
