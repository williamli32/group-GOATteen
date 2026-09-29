package com.goatteen.trading.marketdata;

import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to generate simulated stock prices using Monte Carlo random walk.
 * Loads tradable instruments from the database and generates prices every 2 seconds.
 * Instruments can be loaded from CSV via Flyway migrations.
 */
@Service
public class PriceGeneratorService {
    
    // Monte Carlo Parameters
    private static final double DRIFT = 0.0001;           // Daily drift (0.01%)
    private static final double VOLATILITY = 0.025;       // Daily volatility (2.5%)
    private static final double SPREAD_PERCENTAGE = 0.002; // Bid-ask spread (0.2%)

    private final InstrumentRepository instrumentRepository;
    private final QuoteRepository quoteRepository;
    private final Random random = new Random();
    
    // State variables for continuous price generation per symbol
    // Maps symbol -> current price
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();
    // Maps symbol -> instrument ID (for efficient lookup)
    private final Map<String, Long> instrumentIds = new ConcurrentHashMap<>();

    public PriceGeneratorService(
            InstrumentRepository instrumentRepository,
            QuoteRepository quoteRepository) {
        this.instrumentRepository = instrumentRepository;
        this.quoteRepository = quoteRepository;
    }

    /**
     * Initialize prices for all tradable instruments loaded from database on startup
     */
    @PostConstruct
    public void initializePrices() {
        List<Instrument> tradableInstruments = instrumentRepository
                .findByTradableTrueOrderBySymbolAscExchangeAsc();
        
        for (Instrument instrument : tradableInstruments) {
            BigDecimal seedPrice = generateRandomSeedPrice(instrument.getSymbol());
            currentPrices.put(instrument.getSymbol(), seedPrice);
            instrumentIds.put(instrument.getSymbol(), instrument.getId());
            System.out.println("Initialized " + instrument.getSymbol() + " with seed price: $" + seedPrice);
        }
        
        System.out.println("Price generator initialized for " + tradableInstruments.size() + " instruments");
    }
    
    /**
     * Initialize a symbol with a randomized starting price
     * Realistic price ranges based on market data
     */
    private void initializeSymbol(String symbol) {
        if (!currentPrices.containsKey(symbol)) {
            BigDecimal seedPrice = generateRandomSeedPrice(symbol);
            currentPrices.put(symbol, seedPrice);
            System.out.println("Initialized " + symbol + " with seed price: $" + seedPrice);
        }
    }
    
    /**
     * Generate a realistic random seed price based on the stock symbol
     * Uses typical market price ranges for different categories and industries
     */
    private BigDecimal generateRandomSeedPrice(String symbol) {
        double minPrice;
        double maxPrice;
        
        switch (symbol.toUpperCase()) {
            // Technology - Large cap: $150-250
            case "AAPL", "MSFT", "GOOGL" -> { minPrice = 150; maxPrice = 250; }
            // Technology - Mega cap consumer: $150-200
            case "AMZN", "META" -> { minPrice = 150; maxPrice = 200; }
            // Technology - High volatility: $400-600
            case "NVDA" -> { minPrice = 400; maxPrice = 600; }
            // Technology - EV/Growth: $150-300
            case "TSLA" -> { minPrice = 150; maxPrice = 300; }
            // Technology - Mid cap: $100-200
            case "INTEL", "AMD", "CRM", "IBM", "ORCL" -> { minPrice = 100; maxPrice = 200; }
            // Technology - Premium: $400-700
            case "ADBE", "NFLX" -> { minPrice = 400; maxPrice = 700; }
            // Technology - Semiconductors: $50-150
            case "AVGO" -> { minPrice = 50; maxPrice = 150; }
            
            // Finance - Large banks: $30-200
            case "JPM" -> { minPrice = 160; maxPrice = 200; }
            case "BAC", "WFC" -> { minPrice = 30; maxPrice = 60; }
            // Finance - Investment banks: $300-400
            case "GS", "MS" -> { minPrice = 300; maxPrice = 400; }
            // Finance - Asset managers: $800-1000
            case "BLK" -> { minPrice = 800; maxPrice = 1000; }
            // Finance - Brokers: $60-100
            case "SCHW", "COIN" -> { minPrice = 60; maxPrice = 100; }
            
            // Healthcare - Large pharma: $150-200
            case "JNJ", "ABBV" -> { minPrice = 150; maxPrice = 200; }
            // Healthcare - Insurance/Services: $300-500
            case "UNH" -> { minPrice = 300; maxPrice = 500; }
            // Healthcare - Pharma/Biotech: $20-100
            case "PFE", "AZN", "CVS" -> { minPrice = 20; maxPrice = 100; }
            // Healthcare - Large pharma growth: $600-900
            case "LLY" -> { minPrice = 600; maxPrice = 900; }
            // Healthcare - Pharma/Bio: $250-350
            case "MRK", "GILD" -> { minPrice = 250; maxPrice = 350; }
            
            // Consumer - Beverages/Food: $50-100
            case "KO" -> { minPrice = 50; maxPrice = 80; }
            case "MCD", "SBUX" -> { minPrice = 250; maxPrice = 350; }
            // Consumer - Retail: $70-120
            case "WMT", "TJX", "NKE" -> { minPrice = 70; maxPrice = 120; }
            // Consumer - Home Depot: $350-450
            case "HD" -> { minPrice = 350; maxPrice = 450; }
            // Consumer - Costco: $550-750
            case "COST" -> { minPrice = 550; maxPrice = 750; }
            
            // Energy & Utilities: $30-120
            case "EXC", "NEE", "DUK", "SO" -> { minPrice = 30; maxPrice = 120; }
            
            // Transportation: $20-45
            case "CSX", "DAL", "UAL" -> { minPrice = 20; maxPrice = 45; }
            
            // Aerospace & Defense: $150-550
            case "BA" -> { minPrice = 150; maxPrice = 250; }
            case "LMT" -> { minPrice = 400; maxPrice = 550; }
            case "RTX" -> { minPrice = 80; maxPrice = 140; }
            
            // Default range for any other symbol
            default -> { minPrice = 50; maxPrice = 300; }
        }
        
        // Generate random price within range
        double randomPrice = minPrice + (maxPrice - minPrice) * random.nextDouble();
        return BigDecimal.valueOf(randomPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Generates new quotes for all tracked symbols every 2 seconds using Monte Carlo simulation
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void generateQuotes() {
        // Generate prices for all tracked symbols
        for (String symbol : currentPrices.keySet()) {
            generateQuoteForSymbol(symbol);
        }
    }
    
    /**
     * Generate a quote for a specific stock symbol
     */
    private void generateQuoteForSymbol(String symbol) {
        try {
            Instrument instrument = getInstrument(symbol);
            if (instrument == null) {
                return;
            }

            // Get current price for this symbol
            BigDecimal currentPrice = currentPrices.getOrDefault(symbol, BigDecimal.ONE);
            
            // Generate next price using Monte Carlo random walk
            BigDecimal nextPrice = generateNextPrice(currentPrice);
            currentPrices.put(symbol, nextPrice);

            // Calculate bid and ask prices with spread
            BigDecimal spread = nextPrice.multiply(
                    BigDecimal.valueOf(SPREAD_PERCENTAGE)
            );
            BigDecimal bidPrice = nextPrice.subtract(spread.divide(BigDecimal.valueOf(2)));
            BigDecimal askPrice = nextPrice.add(spread.divide(BigDecimal.valueOf(2)));

            // Create and save quote
            Quote quote = new Quote(
                    instrument,
                    bidPrice,
                    askPrice,
                    nextPrice,
                    LocalDateTime.now()
            );

            quoteRepository.save(quote);
            
        } catch (Exception e) {
            System.err.println("Error generating quote for " + symbol + ": " + e.getMessage());
        }
    }

    /**
     * Monte Carlo random walk for stock price generation
     * Formula: S(t+1) = S(t) * exp((μ - σ²/2)Δt + σ√(Δt)Z)
     * where Z is standard normal random variable
     *
     * @param currentPrice Current stock price
     * @return Next simulated price
     */
    private BigDecimal generateNextPrice(BigDecimal currentPrice) {
        // Generate standard normal random variable (Box-Muller transform)
        double standardNormal = generateStandardNormal();
        
        // Time step (1 interval = 2 seconds, assume 1 trading day = 390 minutes = 11,700 intervals)
        double deltaT = 2.0 / (390 * 60);
        
        // Monte Carlo formula: ln(S(t+1)) = ln(S(t)) + (μ - σ²/2)Δt + σ√(Δt)Z
        double logReturn = (DRIFT - (VOLATILITY * VOLATILITY) / 2) * deltaT 
                         + VOLATILITY * Math.sqrt(deltaT) * standardNormal;
        
        double priceRatio = Math.exp(logReturn);
        
        BigDecimal nextPrice = currentPrice.multiply(
                BigDecimal.valueOf(priceRatio)
        );
        
        // Ensure price doesn't go negative or become unrealistic
        if (nextPrice.doubleValue() < 1.0) {
            nextPrice = currentPrice; // Prevent crashes
        }
        
        return nextPrice.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Generate standard normal random variable using Box-Muller transform
     * @return Random number from standard normal distribution N(0,1)
     */
    private double generateStandardNormal() {
        double u1 = random.nextDouble();
        double u2 = random.nextDouble();
        
        // Avoid log(0)
        if (u1 < 1e-10) u1 = 1e-10;
        
        // Box-Muller transform
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    /**
     * Get or initialize instrument for a symbol
     */
    private Instrument getInstrument(String symbol) {
        // Cache the instrument ID to avoid repeated queries
        Long cachedId = instrumentIds.get(symbol);
        
        if (cachedId == null) {
            // Try to find by symbol and common exchanges
            List<String> commonExchanges = Arrays.asList("NASDAQ", "NYSE", "NYSEARCA");
            Instrument instrument = null;
            
            for (String exchange : commonExchanges) {
                var result = instrumentRepository.findBySymbolIgnoreCaseAndExchangeIgnoreCase(symbol, exchange);
                if (result.isPresent()) {
                    instrument = result.get();
                    break;
                }
            }
            
            if (instrument != null) {
                instrumentIds.put(symbol, instrument.getId());
                return instrument;
            }
            return null;
        }
        
        return instrumentRepository
                .findById(cachedId)
                .orElse(null);
    }

    /**
     * Add a new symbol to track and generate prices for
     * Initializes with a randomized seed price
     */
    public void addSymbol(String symbol) {
        if (!currentPrices.containsKey(symbol)) {
            initializeSymbol(symbol);
        }
    }
    
    /**
     * Remove a symbol from price generation
     */
    public void removeSymbol(String symbol) {
        currentPrices.remove(symbol);
        instrumentIds.remove(symbol);
    }
    
    /**
     * Manually set the current price for a symbol
     */
    public void setCurrentPrice(String symbol, BigDecimal price) {
        currentPrices.put(symbol, price);
    }
    
    /**
     * Manually set the current price (legacy support for single symbol)
     */
    public void setCurrentPrice(BigDecimal price) {
        setCurrentPrice("AAPL", price);
    }

    /**
     * Get the current simulated price for a symbol
     */
    public BigDecimal getCurrentPrice(String symbol) {
        return currentPrices.getOrDefault(symbol, BigDecimal.ZERO);
    }
    
    /**
     * Get the current simulated price (legacy support for single symbol)
     */
    public BigDecimal getCurrentPrice() {
        return getCurrentPrice("AAPL");
    }
    
    /**
     * Get all currently tracked symbols
     */
    public Set<String> getTrackedSymbols() {
        return new HashSet<>(currentPrices.keySet());
    }
}
