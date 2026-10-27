package com.goatteen.trading.fx;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HistoricalFxRatesTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.frankfurter.dev");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HistoricalFxRates rates = new HistoricalFxRates(builder.build());
    private final LocalDate start = LocalDate.of(2026, 10, 17);
    private final LocalDate end = start.plusDays(2);
    private final String url = "https://api.frankfurter.dev/v2/rates?base=USD&quotes=GBP&from=2026-10-10&to=2026-10-19";

    @Test void sameCurrencyNeedsNoProvider() {
        assertEquals(BigDecimal.ONE, rates.between("GBP", "GBP", start, end).floorEntry(end).getValue());
        server.verify();
    }

    @Test void cachesAValidatedSeriesAndCarriesOnlyAnEarlierObservationAcrossAWeekend() {
        server.expect(requestTo(url)).andRespond(withSuccess("""
                [{"date":"2026-10-16","base":"USD","quote":"GBP","rate":0.8},
                 {"date":"2026-10-19","base":"USD","quote":"GBP","rate":0.81}]
                """, MediaType.APPLICATION_JSON));
        var series = rates.between("USD", "GBP", start, end);
        assertEquals(new BigDecimal("0.8"), series.floorEntry(start).getValue());
        assertEquals(new BigDecimal("0.81"), series.floorEntry(end).getValue());
        assertSame(series, rates.between("USD", "GBP", start, end));
        assertThrows(UnsupportedOperationException.class, () -> series.clear());
        server.verify();
    }

    @Test void rejectsProviderFailureInsteadOfInventingARate() {
        server.expect(requestTo(url)).andRespond(withServerError());
        assertThrows(FxRateService.FxRateUnavailableException.class, () -> rates.between("USD", "GBP", start, end));
        server.verify();
    }

    @Test void rejectsAnObservationFromTheWrongCurrencyPair() {
        server.expect(requestTo(url)).andRespond(withSuccess("""
                [{"date":"2026-10-19","base":"EUR","quote":"GBP","rate":0.8}]
                """, MediaType.APPLICATION_JSON));
        assertThrows(FxRateService.FxRateUnavailableException.class, () -> rates.between("USD", "GBP", start, end));
        server.verify();
    }
}
