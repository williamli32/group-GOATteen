package com.goatteen.trading.config;

import com.goatteen.trading.recovery.RecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Startup Recovery Configuration
 * 
 * Automatically runs the RecoveryService on application startup.
 * 
 * Purpose: Recover incomplete settlements after app crash/restart
 * 
 * Execution Timeline:
 *   1. Flyway migrations run (V1-V15)
 *   2. All Spring beans created
 *   3. StartupRecoveryConfig.run() is called (THIS)
 *   4. RecoveryService finds incomplete settlements and completes them
 *   5. Application fully started, ready for requests
 * 
 * Guarantees:
 * - No orders left in SETTLING state
 * - All incomplete settlements resumed from last step
 * - Errors logged but don't crash app
 */
@Configuration
public class StartupRecoveryConfig {

    private static final Logger logger = LoggerFactory.getLogger(StartupRecoveryConfig.class);

    /**
     * Create ApplicationRunner bean that runs RecoveryService on startup
     * 
     * ApplicationRunner beans are executed after Spring Boot initialization completes.
     * Multiple ApplicationRunners execute in order based on @Order annotation.
     * 
     * @param recoveryService The recovery service to run on startup
     * @return ApplicationRunner that executes recovery
     */
    @Bean
    public ApplicationRunner startupRecovery(RecoveryService recoveryService) {
        return args -> {
            try {
                logger.info("=== Starting Recovery Process on Application Startup ===");
                recoveryService.recoverIncompleteSettlements();
                logger.info("=== Recovery Process Completed ===");
            } catch (Exception e) {
                logger.error("Error during startup recovery process", e);
                // Don't rethrow - recovery failure shouldn't crash the application
                // Log it and continue - users can manually retry if needed
            }
        };
    }
}