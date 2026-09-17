package com.goatteen.market.loader;
import com.goatteen.market.dto.MarketData;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class MarketDataCsvLoader {

    private static final String CSV_FILE_PATH = "market-data.csv";
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public List<MarketData> loadMarketData() {
        List<MarketData> marketDataList = new ArrayList<>();

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
                        MarketData data = parseMarketDataLine(line);
                        if (data != null) {
                            marketDataList.add(data);
                        } else {
                            System.err.println("Line " + lineNum + " returned null: " + Arrays.toString(line));
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing line " + lineNum + ": " + Arrays.toString(line));
                        System.err.println("Error: " + e.getMessage());
                    }
                    lineNum++;
                }

                System.out.println("Loaded " + marketDataList.size() + " market data records from CSV");
            }
        } catch (Exception e) {
            System.err.println("Error loading market data CSV file");
            e.printStackTrace();
        }

        return marketDataList;
    }

    private MarketData parseMarketDataLine(String[] line) {
        if (line.length < 15) {
            return null;  // Invalid line
        }

        try {
            MarketData data = new MarketData();
            
            // Column mapping based on CSV header:
            // asset_type, symbol, exchange, country_code, currency, name, price,
            // change, change_percent, volume, market_cap, data_status, 
            // day_high, day_low, open, previous_close, generated_at_utc
            
            data.setSymbol(line[1].trim());                    // symbol
            data.setMarket(line[3].trim());                    // country_code (US, GB, JP, etc.)
            data.setName(line[5].trim());                      // name
            data.setPrice(new BigDecimal(line[6].trim()));     // price
            data.setChange(new BigDecimal(line[7].trim()));    // change
            data.setChangePercent(new BigDecimal(line[8].trim())); // change_percent
            data.setVolume(Long.parseLong(line[9].trim().split("\\.")[0])); // volume (remove decimals)
            data.setHigh(new BigDecimal(line[12].trim()));     // day_high
            data.setLow(new BigDecimal(line[13].trim()));      // day_low
            
            // Parse timestamp
            try {
                data.setTimestamp(LocalDateTime.parse(line[16].trim(), DATE_FORMATTER));
            } catch (Exception e) {
                data.setTimestamp(LocalDateTime.now());
            }
            
            return data;
        } catch (Exception e) {
            System.err.println("Error parsing market data: " + e.getMessage());
            return null;
        }
    }
}