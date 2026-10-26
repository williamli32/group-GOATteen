package com.goatteen.trading.fx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigurationTest {

    @Test
    void autoConfigurationProvidesBuildersAndCreatesHistoricalFxRates() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClientConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HistoricalFxRates.class);
                    RestClient.Builder first = context.getBean(RestClient.Builder.class);
                    RestClient.Builder second = context.getBean(RestClient.Builder.class);
                    assertThat(first).isNotSameAs(second);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import(HistoricalFxRates.class)
    static class ClientConfiguration {
    }
}
