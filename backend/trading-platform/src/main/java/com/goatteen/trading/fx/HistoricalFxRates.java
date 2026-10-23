package com.goatteen.trading.fx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Bounded analytics-only cache; execution uses FxRateService independently. */
@Service
public class HistoricalFxRates {
    private final RestClient client;
    private final Map<Key, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Cached> entry) {
            return size() > 64;
        }
    });

    @Autowired
    public HistoricalFxRates(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        client = builder.baseUrl("https://api.frankfurter.dev").requestFactory(factory).build();
    }

    HistoricalFxRates(RestClient client) { this.client = client; }

    public NavigableMap<LocalDate, BigDecimal> between(String from, String to,
                                                     LocalDate start, LocalDate end) {
        String base = Currency.getInstance(from.toUpperCase(Locale.ROOT)).getCurrencyCode();
        String quote = Currency.getInstance(to.toUpperCase(Locale.ROOT)).getCurrencyCode();
        if (start.isAfter(end)) throw new IllegalArgumentException("Reversed FX date range");
        if (base.equals(quote)) return Collections.unmodifiableNavigableMap(new TreeMap<>(Map.of(start, BigDecimal.ONE)));
        var key = new Key(base, quote, start, end);
        var cached = cache.get(key);
        if (cached != null && cached.expires().isAfter(Instant.now())) return cached.rates();
        try {
            // Include the prior week so weekends can use the last published observation.
            Rate[] rows = client.get().uri(uri -> uri.path("/v2/rates")
                    .queryParam("base", base).queryParam("quotes", quote)
                    .queryParam("from", start.minusDays(7)).queryParam("to", end).build())
                    .retrieve().body(Rate[].class);
            var rates = new TreeMap<LocalDate, BigDecimal>();
            if (rows == null) throw new FxRateService.FxRateUnavailableException("Historical FX is unavailable");
            for (Rate rate : rows) {
                if (rate.date() == null || rate.date().isBefore(start.minusDays(7)) || rate.date().isAfter(end)
                        || !base.equalsIgnoreCase(rate.base()) || !quote.equalsIgnoreCase(rate.quote())
                        || rate.rate() == null || rate.rate().signum() <= 0) {
                    throw new FxRateService.FxRateUnavailableException("Invalid historical FX observation");
                }
                rates.put(rate.date(), rate.rate());
            }
            var immutable = Collections.unmodifiableNavigableMap(rates);
            cache.put(key, new Cached(Instant.now().plusSeconds(300), immutable));
            return immutable;
        } catch (RestClientException failure) {
            throw new FxRateService.FxRateUnavailableException("Historical FX is unavailable", failure);
        }
    }

    private record Rate(LocalDate date, String base, String quote, BigDecimal rate) {}
    private record Key(String from, String to, LocalDate start, LocalDate end) {}
    private record Cached(Instant expires, NavigableMap<LocalDate, BigDecimal> rates) {}
}
