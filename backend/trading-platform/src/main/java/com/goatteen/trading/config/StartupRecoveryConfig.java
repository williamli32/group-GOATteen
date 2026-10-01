package com.goatteen.trading.config;

import com.goatteen.trading.recovery.RecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StartupRecoveryConfig {

    private static final Logger logger = LoggerFactory.getLogger(
            StartupRecoveryConfig.class);

    @Bean
    public ApplicationRunner startupRecovery(
            RecoveryService recoveryService) {

        return args -> {

            try {

                logger.info(
                        "Starting application recovery");

                recoveryService
                        .recoverOnStartup();

                logger.info(
                        "Application recovery completed");

            } catch (Exception e) {

                /*
                 * Don't make the entire application unavailable
                 * because one order requires manual recovery.
                 */
                logger.error(
                        "Startup recovery encountered an error",
                        e);
            }
        };
    }
}