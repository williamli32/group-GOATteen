package com.goatteen.market.loader;

import com.goatteen.market.dto.HistoricalPrice;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class HistoricalDataCsvLoader {

    private static final String CSV_FILE_PATH = "historical-market-data.csv";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public List<HistoricalPrice> loadHistoricalData() {
        List<HistoricalPrice> historicalPriceList = new ArrayList<>();

        try (var inputStream = getClass().getClassLoader()
                .getResourceAsStream(CSV_FILE_PATH);
             var reader = new InputStreamReader(inputStream)) {

            var csvParser = new CSVParserBuilder()
                    .withSeparator(',')
                    .build();

            try (var csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(csvParser)
                    .withSkipLines(1)  // Skip header row
                    .build()) {

                String[] line;
                int lineNum = 1;
                while ((line = csvReader.readNext()) != null) {
                    try {
                        HistoricalPrice data = parseHistoricalPriceLine(line);
                        if (data != null) {
                            historicalPriceList.add(data);
                        } else {
                            System.err.println("Line " + lineNum + " returned null: " + Arrays.toString(line));
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing line " + lineNum + ": " + Arrays.toString(line));
                        System.err.println("Error: " + e.getMessage());
                    }
                    lineNum++;
                }

                System.out.println("Loaded " + historicalPriceList.size() + " historical price records from CSV");
            }
        } catch (Exception e) {
            System.err.println("Error loading historical market data CSV file");
            e.printStackTrace();
        }

        return historicalPriceList;
    }

    private HistoricalPrice parseHistoricalPriceLine(String[] line) {
        if (line.length < 7) {
            return null;  // Invalid line
        }

        try {
            // Column mapping:
            // symbol, date, open, high, low, close, volume
            String symbol = line[0].trim();
            LocalDate date = LocalDate.parse(line[1].trim(), DATE_FORMATTER);
            BigDecimal open = new BigDecimal(line[2].trim());
            BigDecimal high = new BigDecimal(line[3].trim());
            BigDecimal low = new BigDecimal(line[4].trim());
            BigDecimal close = new BigDecimal(line[5].trim());
            Long volume = Long.parseLong(line[6].trim().split("\\.")[0]);

            // Validate OHLCV data
            if (!isValidOHLCV(open, high, low, close, volume)) {
                System.err.println("Validation failed for line: " + Arrays.toString(line));
                return null;
            }

            return new HistoricalPrice(symbol, date, open, high, low, close, volume);

        } catch (Exception e) {
            System.err.println("Error parsing historical price data: " + e.getMessage());
            return null;
        }
    }

    /**
     * Validate OHLCV (Open, High, Low, Close, Volume) constraints
     */
    private boolean isValidOHLCV(BigDecimal open, BigDecimal high, BigDecimal low,
                                 BigDecimal close, Long volume) {
        // Check that prices are positive
        if (open.compareTo(BigDecimal.ZERO) <= 0 ||
            high.compareTo(BigDecimal.ZERO) <= 0 ||
            low.compareTo(BigDecimal.ZERO) <= 0 ||
            close.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Check that low <= open <= high
        if (low.compareTo(open) > 0 || open.compareTo(high) > 0) {
            return false;
        }

        // Check that low <= close <= high
        if (low.compareTo(close) > 0 || close.compareTo(high) > 0) {
            return false;
        }

        // Check that volume is non-negative
        if (volume < 0) {
            return false;
        }

        return true;
    }
}
