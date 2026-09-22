import { Component, OnInit, OnDestroy, Input } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MarketDataService } from '../../../core/services/market-data.service';
import { Subject, interval } from 'rxjs';
import { takeUntil, switchMap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

/**
 * Component showing real-time price updates and order placement for a specific stock
 * Configurable symbol - defaults to AAPL
 * This demonstrates the architecture for trading any stock symbol
 */
@Component({
  selector: 'app-live-trading',
  templateUrl: './live-trading.component.html',
  styleUrls: ['./live-trading.component.scss'],
  standalone: true,
  imports: [CommonModule, FormsModule, CurrencyPipe, DatePipe]
})
export class LiveTradingComponent implements OnInit, OnDestroy {

  // 50 major stocks spanning diverse industries
  availableSymbols = [
    // Technology
    { symbol: 'AAPL', name: 'Apple' },
    { symbol: 'MSFT', name: 'Microsoft' },
    { symbol: 'GOOGL', name: 'Google' },
    { symbol: 'AMZN', name: 'Amazon' },
    { symbol: 'NVDA', name: 'NVIDIA' },
    { symbol: 'TSLA', name: 'Tesla' },
    { symbol: 'META', name: 'Meta' },
    { symbol: 'INTEL', name: 'Intel' },
    { symbol: 'AMD', name: 'AMD' },
    { symbol: 'CRM', name: 'Salesforce' },
    { symbol: 'IBM', name: 'IBM' },
    { symbol: 'ORCL', name: 'Oracle' },
    { symbol: 'ADBE', name: 'Adobe' },
    { symbol: 'NFLX', name: 'Netflix' },
    { symbol: 'AVGO', name: 'Broadcom' },
    
    // Finance
    { symbol: 'JPM', name: 'JPMorgan' },
    { symbol: 'BAC', name: 'Bank of America' },
    { symbol: 'WFC', name: 'Wells Fargo' },
    { symbol: 'GS', name: 'Goldman Sachs' },
    { symbol: 'MS', name: 'Morgan Stanley' },
    { symbol: 'BLK', name: 'BlackRock' },
    { symbol: 'SCHW', name: 'Charles Schwab' },
    { symbol: 'COIN', name: 'Coinbase' },
    
    // Healthcare & Pharma
    { symbol: 'JNJ', name: 'Johnson & Johnson' },
    { symbol: 'UNH', name: 'UnitedHealth' },
    { symbol: 'PFE', name: 'Pfizer' },
    { symbol: 'ABBV', name: 'AbbVie' },
    { symbol: 'TMO', name: 'Thermo Fisher' },
    { symbol: 'CVS', name: 'CVS Health' },
    { symbol: 'LLY', name: 'Eli Lilly' },
    { symbol: 'AZN', name: 'AstraZeneca' },
    { symbol: 'MRK', name: 'Merck' },
    { symbol: 'GILD', name: 'Gilead' },
    
    // Consumer & Retail
    { symbol: 'KO', name: 'Coca-Cola' },
    { symbol: 'MCD', name: 'McDonald\'s' },
    { symbol: 'SBUX', name: 'Starbucks' },
    { symbol: 'WMT', name: 'Walmart' },
    { symbol: 'TJX', name: 'TJX Companies' },
    { symbol: 'NKE', name: 'Nike' },
    { symbol: 'HD', name: 'Home Depot' },
    { symbol: 'COST', name: 'Costco' },
    
    // Industrial & Energy
    { symbol: 'EXC', name: 'Exelon' },
    { symbol: 'NEE', name: 'NextEra Energy' },
    { symbol: 'DUK', name: 'Duke Energy' },
    { symbol: 'SO', name: 'Southern Company' },
    { symbol: 'CSX', name: 'CSX Corporation' },
    
    // Transportation
    { symbol: 'DAL', name: 'Delta Air' },
    { symbol: 'UAL', name: 'United Airlines' },
    { symbol: 'BA', name: 'Boeing' },
    
    // Aerospace & Defense
    { symbol: 'LMT', name: 'Lockheed Martin' },
    { symbol: 'RTX', name: 'Raytheon' }
  ];

  @Input() symbol: string = 'AAPL';  // Configurable stock symbol

  // UI state
  marketInstruments: any[] = [];
  selectedInstrument: any = null;
  connected = false;
  buyQuantity: number = 1;
  sellQuantity: number = 1;
  selectedAccountId: number = 1;
  loading = false;
  errorMessage: string | null = null;
  
  // Store price data for all tracked stocks
  stockPrices = new Map<string, any>();

  private destroy$ = new Subject<void>();

  constructor(private marketDataService: MarketDataService) {}

  /**
   * Computed property for current price data
   * Returns formatted price info for the selected instrument
   */
  get currentPrice(): any {
    if (!this.selectedInstrument?.latestQuote) {
      return null;
    }
    const quote = this.selectedInstrument.latestQuote;
    return {
      bidPrice: quote.bidPrice,
      askPrice: quote.askPrice,
      lastPrice: quote.lastPrice,
      midPrice: ((quote.bidPrice || 0) + (quote.askPrice || 0)) / 2,
      timestamp: quote.quotedAt || new Date().toISOString(),
      sequenceNumber: 0 // Not tracked in REST API, default to 0
    };
  }

  ngOnInit(): void {
    // Load market data initially
    this.loadMarketData();
    
    // Poll for market data updates every 2 seconds
    interval(2000)
      .pipe(
        takeUntil(this.destroy$),
        switchMap(() => this.marketDataService.getInstruments()),
        catchError((error) => {
          console.error('Error fetching market data:', error);
          this.errorMessage = 'Failed to load market data';
          return of([]);
        })
      )
      .subscribe((instruments: any[]) => {
        this.marketInstruments = instruments;
        this.connected = true;
        this.errorMessage = null;
        
        // Update the price map
        instruments.forEach((instrument: any) => {
          if (instrument.latestQuote) {
            this.stockPrices.set(instrument.symbol, {
              symbol: instrument.symbol,
              bidPrice: instrument.latestQuote.bidPrice,
              askPrice: instrument.latestQuote.askPrice,
              lastPrice: instrument.latestQuote.lastPrice,
              timestamp: new Date().toISOString()
            });
          }
        });
        
        // Update selected instrument if it matches current symbol
        const selected = instruments.find((i: any) => i.symbol === this.symbol);
        if (selected) {
          this.selectedInstrument = selected;
        }
      });
  }

  private loadMarketData(): void {
    this.loading = true;
    this.marketDataService.getInstruments()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (instruments: any[]) => {
          this.marketInstruments = instruments;
          this.connected = true;
          this.loading = false;
          
          // Initialize stock prices map
          instruments.forEach((instrument: any) => {
            if (instrument.latestQuote) {
              this.stockPrices.set(instrument.symbol, {
                symbol: instrument.symbol,
                bidPrice: instrument.latestQuote.bidPrice,
                askPrice: instrument.latestQuote.askPrice,
                lastPrice: instrument.latestQuote.lastPrice,
                timestamp: new Date().toISOString()
              });
            }
          });
          
          // Select the initial symbol
          const selected = instruments.find((i: any) => i.symbol === this.symbol);
          if (selected) {
            this.selectedInstrument = selected;
          }
        },
        error: (error: any) => {
          console.error('Error loading market data:', error);
          this.errorMessage = 'Failed to load market data';
          this.connected = false;
          this.loading = false;
        }
      });
  }

  /**
   * Place a BUY order at the current ask price
   */
  placeBuyOrder(): void {
    if (!this.selectedInstrument?.latestQuote) {
      alert('No price data available');
      return;
    }

    const order = {
      symbol: this.symbol,
      quantity: this.buyQuantity,
      limitPrice: this.selectedInstrument.latestQuote.askPrice,  // Buy at ask
      orderType: 'BUY',
      accountId: this.selectedAccountId
    };

    console.log('Buy Order Placed:', order);
    alert(`BUY Order: ${this.buyQuantity} shares of ${this.symbol} at $${this.selectedInstrument.latestQuote.askPrice}`);
  }

  /**
   * Place a SELL order at the current bid price
   */
  placeSellOrder(): void {
    if (!this.selectedInstrument?.latestQuote) {
      alert('No price data available');
      return;
    }

    const order = {
      symbol: this.symbol,
      quantity: this.sellQuantity,
      limitPrice: this.selectedInstrument.latestQuote.bidPrice,  // Sell at bid
      orderType: 'SELL',
      accountId: this.selectedAccountId
    };

    console.log('Sell Order Placed:', order);
    alert(`SELL Order: ${this.sellQuantity} shares of ${this.symbol} at $${this.selectedInstrument.latestQuote.bidPrice}`);
  }

  /**
   * Change the trading symbol
   */
  onSymbolChange(): void {
    // Update selected instrument when symbol changes
    const selected = this.marketInstruments.find((i: any) => i.symbol === this.symbol);
    if (selected) {
      this.selectedInstrument = selected;
      console.log('Switched to symbol:', this.symbol);
    }
  }

  /**
   * Cleanup on component destroy
   */
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Get stocks grouped by industry category
   */
  getGroupedSymbols(): any[] {
    return [
      {
        category: 'Technology',
        stocks: this.availableSymbols.slice(0, 15)
      },
      {
        category: 'Finance',
        stocks: this.availableSymbols.slice(15, 23)
      },
      {
        category: 'Healthcare & Pharma',
        stocks: this.availableSymbols.slice(23, 33)
      },
      {
        category: 'Consumer & Retail',
        stocks: this.availableSymbols.slice(33, 41)
      },
      {
        category: 'Industrial & Energy',
        stocks: this.availableSymbols.slice(41, 46)
      },
      {
        category: 'Transportation',
        stocks: this.availableSymbols.slice(46, 49)
      },
      {
        category: 'Aerospace & Defense',
        stocks: this.availableSymbols.slice(49, 51)
      }
    ];
  }

  /**
   * Get all stocks with their current prices
   */
  getStocksWithPrices(): any[] {
    return this.availableSymbols.map(stock => {
      const priceData = this.stockPrices.get(stock.symbol);
      return {
        symbol: stock.symbol,
        name: stock.name,
        price: priceData ? {
          bidPrice: priceData.bidPrice,
          askPrice: priceData.askPrice,
          lastPrice: priceData.lastPrice,
          midPrice: ((priceData.bidPrice || 0) + (priceData.askPrice || 0)) / 2,
          timestamp: priceData.timestamp
        } : null
      };
    });
  }
}
