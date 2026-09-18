package com.goatteen.market.loader;

import com.goatteen.market.dto.MarketData;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class MarketDataCsvLoader {

    private static final String CSV_FILE_PATH = "market-data.csv";

    public List<MarketData> loadMarketData() {

        List<MarketData> marketDataList = new ArrayList<>();

        InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream(
                        CSV_FILE_PATH);

        if (inputStream == null) {

            throw new IllegalStateException(
                    "Unable to find "
                            + CSV_FILE_PATH);
        }

        try (
                inputStream;
                InputStreamReader reader = new InputStreamReader(
                        inputStream)) {

            var csvParser = new CSVParserBuilder()
                    .withSeparator(',')
                    .build();

            try (
                    var csvReader = new CSVReaderBuilder(
                            reader)
                            .withCSVParser(
                                    csvParser)
                            .withSkipLines(1)
                            .build()) {

                String[] line;

                int lineNumber = 2;

                while ((line = csvReader
                        .readNext()) != null) {

                    /*
                     * Ignore completely blank rows.
                     */
                    if (line.length == 0
                            ||
                            Arrays
                                    .stream(line)
                                    .allMatch(
                                            String::isBlank)) {

                        lineNumber++;

                        continue;
                    }

                    try {

                        MarketData data = parseMarketDataLine(
                                line);

                        marketDataList.add(
                                data);

                    } catch (Exception e) {

                        System.err.println(
                                "Unable to parse market-data CSV line "
                                        + lineNumber
                                        + ": "
                                        + e.getMessage());
                    }

                    lineNumber++;
                }
            }

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to load simulated market data",
                    e);
        }

        System.out.println(
                "Loaded "
                        + marketDataList.size()
                        + " simulated market records");

        return marketDataList;
    }

    private MarketData parseMarketDataLine(
            String[] line) {

        /*
         * CSV columns:
         *
         * 0 asset_type
         * 1 symbol
         * 2 exchange
         * 3 country_code
         * 4 currency
         * 5 name
         * 6 price
         * 7 change
         * 8 change_percent
         * 9 volume
         * 10 market_cap
         * 11 data_status
         * 12 day_high
         * 13 day_low
         * 14 open
         * 15 previous_close
         * 16 generated_at_utc
         */
        if (line.length < 17) {

            throw new IllegalArgumentException(
                    "Expected 17 columns but found "
                            + line.length);
        }

        MarketData data = new MarketData();

        data.setAssetType(
                line[0].trim());

        data.setSymbol(
                line[1].trim());

        data.setExchange(
                line[2].trim());

        data.setCountryCode(
                line[3].trim());

        data.setCurrency(
                line[4].trim());

        /*
         * Preserve the old field temporarily
         * for compatibility with the existing
         * experimental dashboard.
         */
        data.setMarket(
                line[3].trim());

        data.setName(
                line[5].trim());

        data.setPrice(
                new BigDecimal(
                        line[6].trim()));

        data.setChange(
                new BigDecimal(
                        line[7].trim()));

        data.setChangePercent(
                new BigDecimal(
                        line[8].trim()));

        data.setVolume(
                parseVolume(
                        line[9]));

        data.setHigh(
                new BigDecimal(
                        line[12].trim()));

        data.setLow(
                new BigDecimal(
                        line[13].trim()));

        data.setTimestamp(
                parseTimestamp(
                        line[16]));

        return data;
    }

    private Long parseVolume(
            String rawVolume) {

        BigDecimal volume = new BigDecimal(
                rawVolume.trim());

        return volume.longValue();
    }

    private LocalDateTime parseTimestamp(
            String rawTimestamp) {

        try {

            return OffsetDateTime
                    .parse(
                            rawTimestamp.trim())
                    .toLocalDateTime();

        } catch (Exception e) {

            return LocalDateTime.now();
        }
    }
}