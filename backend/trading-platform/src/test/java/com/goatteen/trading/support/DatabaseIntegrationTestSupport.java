package com.goatteen.trading.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.transaction.TestTransaction;

/** Keeps committed/concurrent tests isolated without wrapping them in a rollback transaction. */
public abstract class DatabaseIntegrationTestSupport {
    @Autowired private JdbcTemplate fixtureJdbc;
    @Autowired @Qualifier("applicationTaskExecutor") private ThreadPoolTaskExecutor reportingExecutor;
    protected TestDatabaseFixtures fixtures;

    @BeforeEach
    protected final void startFixtureTracking() {
        fixtures = new TestDatabaseFixtures(fixtureJdbc);
    }

    @AfterEach
    protected final void removeFixtures() throws InterruptedException {
        // Spring rolls back method-level @Transactional tests after @AfterEach.
        // Opening a separate JDBC transaction here could interfere with that rollback.
        if (TestTransaction.isActive()) {
            return;
        }
        if (fixtures != null) {
            TestDatabaseFixtures.awaitReporting(reportingExecutor);
            fixtures.close();
        }
    }

    protected final void recordInitialLifecycle(long orderId) {
        TestDatabaseFixtures.recordInitialLifecycle(fixtureJdbc, orderId);
    }
}
