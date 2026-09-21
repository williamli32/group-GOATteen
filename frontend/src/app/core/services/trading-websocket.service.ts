import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import Stomp from 'stompjs';
import SockJS from 'sockjs-client';

export interface PriceUpdate {
  symbol: string;
  bidPrice: number;
  askPrice: number;
  lastPrice: number;
  midPrice: number;
  timestamp: string;
  sequenceNumber: number;
}

export interface OrderMessage {
  symbol: string;
  quantity: number;
  limitPrice?: number;
  orderType: 'BUY' | 'SELL';
  accountId: number;
}

export interface OrderConfirmation {
  symbol: string;
  quantity: number;
  orderType: string;
  status: string;
  message: string;
}

/**
 * Service to handle WebSocket connections for real-time trading
 * - Receives live price updates for multiple symbols
 * - Sends trading orders
 * - Handles order confirmations
 */
@Injectable({
  providedIn: 'root'
})
export class TradingWebSocketService {

  private stompClient: any;
  private connected = false;
  private subscribedSymbols = new Set<string>();

  // Observable streams for components
  public priceUpdates$ = new Subject<PriceUpdate>();
  public orderConfirmations$ = new Subject<OrderConfirmation>();
  public connectionStatus$ = new Subject<boolean>();

  constructor() {}

  /**
   * Connect to WebSocket server
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = new SockJS('http://localhost:8080/ws');
      this.stompClient = Stomp.over(socket);

      this.stompClient.connect({}, (frame: any) => {
        console.log('WebSocket Connected:', frame);
        this.connected = true;
        this.connectionStatus$.next(true);

        // Subscribe to order confirmations
        this.stompClient.subscribe('/topic/orders/confirmation', (message: any) => {
          const confirmation: OrderConfirmation = JSON.parse(message.body);
          this.orderConfirmations$.next(confirmation);
        });

        resolve();
      }, (error: any) => {
        console.error('WebSocket Connection Error:', error);
        this.connected = false;
        this.connectionStatus$.next(false);
        reject(error);
      });
    });
  }

  /**
   * Subscribe to price updates for a specific symbol
   * Can be called multiple times for different symbols
   */
  subscribeToSymbol(symbol: string): void {
    if (!this.connected) {
      console.error('WebSocket not connected');
      return;
    }

    if (this.subscribedSymbols.has(symbol)) {
      console.warn('Already subscribed to ' + symbol);
      return;
    }

    const topic = '/topic/prices/' + symbol;
    this.stompClient.subscribe(topic, (message: any) => {
      const priceUpdate: PriceUpdate = JSON.parse(message.body);
      this.priceUpdates$.next(priceUpdate);
    });

    this.subscribedSymbols.add(symbol);
    console.log('Subscribed to ' + symbol + ' price updates');
  }

  /**
   * Unsubscribe from price updates for a specific symbol
   */
  unsubscribeFromSymbol(symbol: string): void {
    this.subscribedSymbols.delete(symbol);
    console.log('Unsubscribed from ' + symbol);
  }

  /**
   * Get all currently subscribed symbols
   */
  getSubscribedSymbols(): string[] {
    return Array.from(this.subscribedSymbols);
  }

  /**
   * Disconnect from WebSocket
   */
  disconnect(): void {
    if (this.stompClient && this.stompClient.connected) {
      this.stompClient.disconnect(() => {
        this.connected = false;
        this.connectionStatus$.next(false);
        this.subscribedSymbols.clear();
        console.log('WebSocket Disconnected');
      });
    }
  }

  /**
   * Send a BUY order
   */
  buyOrder(order: OrderMessage): void {
    if (!this.connected) {
      console.error('WebSocket not connected');
      return;
    }

    order.orderType = 'BUY';
    this.stompClient.send('/app/order/buy', {}, JSON.stringify(order));
  }

  /**
   * Send a SELL order
   */
  sellOrder(order: OrderMessage): void {
    if (!this.connected) {
      console.error('WebSocket not connected');
      return;
    }

    order.orderType = 'SELL';
    this.stompClient.send('/app/order/sell', {}, JSON.stringify(order));
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.connected;
  }
}
