package com.goatteen.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * LEAP Trading Platform Backend Application
 * 
 * Spring Boot entry point for the trading platform microservice.
 * 
 * Features:
 * - REST API for order management and portfolio tracking
 * - JWT-based authentication and authorization
 * - PostgreSQL persistence with Flyway migrations
 * - Scheduled market data synchronization
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan("com.goatteen.trading.auth.security")
public class TradingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingPlatformApplication.class, args);
    }

}
