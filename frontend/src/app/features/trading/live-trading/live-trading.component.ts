import { Component, OnInit, OnDestroy, Input } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TradingWebSocketService, PriceUpdate, OrderMessage } from '../../../core/services/trading-websocket.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

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

  currentPrice: PriceUpdate | null = null;
  connected = false;
  buyQuantity: number = 1;
  sellQuantity: number = 1;
  selectedAccountId: number = 1;
  
  // Store price data for all tracked stocks
  stockPrices = new Map<string, PriceUpdate>();

  private destroy$ = new Subject<void>();

  constructor(private websocketService: TradingWebSocketService) {}

  ngOnInit(): void {
    // Connect to WebSocket
    this.websocketService.connect().then(() => {
      console.log('Connected to trading WebSocket');
      
      // Subscribe to price updates for all available stocks
      this.availableSymbols.forEach(stock => {
        this.websocketService.subscribeToSymbol(stock.symbol);
      });
      
      // Also subscribe to the initially selected symbol
      this.websocketService.subscribeToSymbol(this.symbol);
    }).catch((error: any) => {
      console.error('Failed to connect:', error);
    });

    // Subscribe to ALL price updates and track them
    this.websocketService.priceUpdates$
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe((priceUpdate: PriceUpdate) => {
        // Store all price updates in the map
        this.stockPrices.set(priceUpdate.symbol, priceUpdate);
        
        // Also update current price if it matches the selected symbol
        if (priceUpdate.symbol === this.symbol) {
          this.currentPrice = priceUpdate;
          console.log('Price Update for ' + this.symbol + ':', priceUpdate);
        }
      });

    // Monitor connection status
    this.websocketService.connectionStatus$
      .pipe(takeUntil(this.destroy$))
      .subscribe((status: boolean) => {
        this.connected = status;
      });

    // Subscribe to order confirmations
    this.websocketService.orderConfirmations$
      .pipe(takeUntil(this.destroy$))
      .subscribe((confirmation: any) => {
        console.log('Order Confirmation:', confirmation);
        alert(`Order ${confirmation.orderType} confirmed: ${confirmation.quantity} shares of ${confirmation.symbol}`);
      });
  }

  /**
   * Place a BUY order at the current ask price
   */
  placeBuyOrder(): void {
    if (!this.currentPrice) {
      alert('No price data available');
      return;
    }

    const order: OrderMessage = {
      symbol: this.symbol,
      quantity: this.buyQuantity,
      limitPrice: Number(this.currentPrice.askPrice),  // Buy at ask
      orderType: 'BUY',
      accountId: this.selectedAccountId
    };

    this.websocketService.buyOrder(order);
  }

  /**
   * Place a SELL order at the current bid price
   */
  placeSellOrder(): void {
    if (!this.currentPrice) {
      alert('No price data available');
      return;
    }

    const order: OrderMessage = {
      symbol: this.symbol,
      quantity: this.sellQuantity,
      limitPrice: Number(this.currentPrice.bidPrice),  // Sell at bid
      orderType: 'SELL',
      accountId: this.selectedAccountId
    };

    this.websocketService.sellOrder(order);
  }

  /**
   * Change the trading symbol
   */
  onSymbolChange(): void {
    // Clear current price when switching symbols
    this.currentPrice = null;
    
    // Subscribe to the new symbol's price updates
    this.websocketService.subscribeToSymbol(this.symbol);
    console.log('Switched to symbol:', this.symbol);
  }

  /**
   * Get stocks with their current prices for table display
   */
  getStocksWithPrices() {
    return this.availableSymbols.map(stock => ({
      ...stock,
      price: this.stockPrices.get(stock.symbol) || null
    }));
  }

  /**
   * Group symbols by category for dropdown display
   */
  getGroupedSymbols() {
    const categories: { [key: string]: any[] } = {
      'Technology': this.availableSymbols.slice(0, 15),
      'Finance & Banking': this.availableSymbols.slice(15, 23),
      'Healthcare & Pharma': this.availableSymbols.slice(23, 33),
      'Consumer & Retail': this.availableSymbols.slice(33, 41),
      'Industrial & Energy': this.availableSymbols.slice(41, 46),
      'Transportation': this.availableSymbols.slice(46, 49),
      'Aerospace & Defense': this.availableSymbols.slice(49, 51)
    };

    return Object.keys(categories).map(category => ({
      category,
      stocks: categories[category]
    }));
  }

  /**
   * Cleanup on component destroy
   */
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.websocketService.disconnect();
  }
}
