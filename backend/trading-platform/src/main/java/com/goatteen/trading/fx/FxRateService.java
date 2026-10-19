package com.goatteen.trading.fx;

import com.goatteen.trading.order.OrderSide;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Locale;

/**
 * Converts trade notionals from the instrument's quotation currency into
 * the account currency using Frankfurter's DAILY reference FX rates.
 * Suitable for the simulated LEAP platform, not an executable FX quote.
 */
@Service
public class FxRateService {
    private final RestClient client;

    public FxRateService(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.client = builder.baseUrl("https://api.frankfurter.dev")
                .requestFactory(factory)
                .build();
    }

    /** One unit of {@code from} purchases {@code rate} units of {@code to}. */
    public record FxQuote(String from, String to, BigDecimal rate,
                          LocalDate asOf, String provider) { }

    private record ApiRate(LocalDate date, String base,
                           String quote, BigDecimal rate) { }

    public static class FxRateUnavailableException extends RuntimeException {
        public FxRateUnavailableException(String message) { super(message); }
        public FxRateUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public FxQuote quote(String fromCurrency, String toCurrency) {
        String from = normalizedCurrency(fromCurrency);
        String to = normalizedCurrency(toCurrency);
        if (from.equals(to)) {
            return new FxQuote(from, to, BigDecimal.ONE,
                    LocalDate.now(ZoneOffset.UTC), "SAME_CURRENCY");
        }

        try {
            ApiRate result = client.get()
                    .uri("/v2/rate/{base}/{quote}", from, to)
                    .retrieve()
                    .body(ApiRate.class);

            if (result == null || result.date() == null ||
                    result.rate() == null || result.rate().signum() <= 0 ||
                    !from.equalsIgnoreCase(result.base()) ||
                    !to.equalsIgnoreCase(result.quote())) {
                throw new FxRateUnavailableException("FX provider supplied an invalid rate");
            }

            long ageDays = ChronoUnit.DAYS.between(
                    result.date(), LocalDate.now(ZoneOffset.UTC));
            if (ageDays < 0 || ageDays > 5) {
                throw new FxRateUnavailableException(
                        "FX rate is stale or future-dated: " + result.date());
            }
            return new FxQuote(from, to, result.rate().setScale(12, RoundingMode.HALF_UP), result.date(),
                    "FRANKFURTER_DAILY");
        } catch (RestClientException ex) {
            throw new FxRateUnavailableException("FX provider is unavailable", ex);
        }
    }

    /** Positive amount in the account's currency. BUY rounds debit up;
     * SELL rounds credit down (conservative at a currency minor unit). */
    public BigDecimal settlementAmount(BigDecimal instrumentNotional,
                                        FxQuote quote, OrderSide side) {
        if (instrumentNotional == null || instrumentNotional.signum() <= 0 ||
                quote == null || quote.rate() == null ||
                quote.rate().signum() <= 0 || side == null) {
            throw new IllegalArgumentException("Invalid FX settlement inputs");
        }
        int minorUnits = Currency.getInstance(quote.to()).getDefaultFractionDigits();
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Currency has no supported minor unit");
        }
        return instrumentNotional.multiply(quote.rate())
                .setScale(minorUnits,
                        quote.from().equals(quote.to()) ? RoundingMode.HALF_UP
                                : side == OrderSide.BUY ? RoundingMode.CEILING : RoundingMode.FLOOR);
    }

    private String normalizedCurrency(String value) {
        try {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            Currency.getInstance(normalized);
            return normalized;
        } catch (RuntimeException ex) {
            throw new FxRateUnavailableException("Unsupported currency: " + value, ex);
        }
    }
}
