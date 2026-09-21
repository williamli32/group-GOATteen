package com.goatteen.trading;

import com.goatteen.trading.auth.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
public class TradingPlatformApplication {

	public static void main(
			String[] args) {

		SpringApplication.run(
				TradingPlatformApplication.class,
				args);
	}
}