package com.goatteen.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableScheduling
public class MarketDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                MarketDataApplication.class,
                args);
    }

    /*
     * Temporary development CORS support.
     *
     * Angular still talks directly to this service
     * on the experimental branch. We will remove
     * that direct dependency later and route market
     * data through the trading-platform backend.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {

        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(
                    CorsRegistry registry) {

                registry
                        .addMapping("/**")
                        .allowedOrigins(
                                "http://localhost:4200")
                        .allowedMethods(
                                "GET",
                                "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}