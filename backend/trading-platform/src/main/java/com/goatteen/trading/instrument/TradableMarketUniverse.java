package com.goatteen.trading.instrument;

import java.util.Locale;
import java.util.Set;

public final class TradableMarketUniverse {

    private static final Set<String> INSTRUMENT_KEYS = Set.of(

            // =========================
            // UNITED STATES — 20
            // =========================

            "AAPL|NASDAQ",
            "MSFT|NASDAQ",
            "GOOGL|NASDAQ",
            "AMZN|NASDAQ",
            "NVDA|NASDAQ",
            "META|NASDAQ",
            "TSLA|NASDAQ",

            "JPM|NYSE",
            "V|NYSE",
            "MA|NYSE",

            "WMT|NASDAQ",

            "KO|NYSE",
            "PEP|NASDAQ",

            "NFLX|NASDAQ",
            "DIS|NYSE",

            "AMD|NASDAQ",
            "INTC|NASDAQ",

            "CRM|NYSE",
            "ORCL|NYSE",
            "XOM|NYSE",

            // =========================
            // INDIA — 15
            // =========================

            "RELIANCE|NSE_IN",
            "TCS|NSE_IN",
            "HDFCBANK|NSE_IN",
            "INFY|NSE_IN",
            "ICICIBANK|NSE_IN",
            "ITC|NSE_IN",
            "SBIN|NSE_IN",
            "BHARTIARTL|NSE_IN",
            "KOTAKBANK|NSE_IN",
            "LT|NSE_IN",
            "AXISBANK|NSE_IN",
            "MARUTI|NSE_IN",
            "SUNPHARMA|NSE_IN",
            "HCLTECH|NSE_IN",
            "BAJFINANCE|NSE_IN",

            // =========================
            // UNITED KINGDOM — 10
            // =========================

            "SHEL|LSE",
            "AZN|LSE",
            "HSBA|LSE",
            "ULVR|LSE",
            "BP|LSE",
            "RIO|LSE",
            "GSK|LSE",
            "VOD|LSE",
            "BARC|LSE",
            "LLOY|LSE",

            // =========================
            // FOREIGN EXCHANGE — 3
            // =========================

            "GBPUSD|FOREX",
            "EURUSD|FOREX",
            "USDJPY|FOREX",

            // =========================
            // CRYPTO — 2
            // =========================

            "BTCUSD|CRYPTO",
            "ETHUSD|CRYPTO");

    private TradableMarketUniverse() {
    }

    public static boolean contains(
            String symbol,
            String exchange) {

        if (symbol == null
                || exchange == null) {

            return false;
        }

        String key = (symbol.trim()
                + "|"
                + exchange.trim())
                .toUpperCase(
                        Locale.ROOT);

        return INSTRUMENT_KEYS.contains(
                key);
    }

    public static int size() {

        return INSTRUMENT_KEYS.size();
    }
}