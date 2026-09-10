import { Component, ViewChild, ElementRef, OnInit, AfterViewInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { priceHistory, positions, orderHistory } from '../data/mock-data';

interface PriceData {
  time: string;
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
  price: number;
}

interface Position {
  symbol: string;
  name: string;
  shares: number;
  avgCost: number;
  currentPrice: number;
  sector: string;
}

interface Order {
  id: string;
  symbol: string;
  side: string;
  qty: number;
  price: number;
  status: string;
  time: string;
  date: string;
  total: number;
}

function fmt(n: number): string {
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
}

function pct(curr: number, avg: number): string {
  return (((curr - avg) / avg) * 100).toFixed(2);
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit, AfterViewInit {
  @ViewChild('priceChart') priceChart!: ElementRef<HTMLCanvasElement>;

  symbols: string[] = ['AAPL', 'MSFT', 'NVDA', 'JPM', 'V', 'XOM'];
  ticker: string = 'AAPL';
  orderType: string = 'MARKET';
  side: string = 'BUY';
  qty: number = 100;
  limitPrice: number = 189.73;
  submitted: boolean = false;
  positions: Position[] = positions;
  orderHistory: Order[] = orderHistory;
  priceHistory: PriceData[] = priceHistory;

  lastPrice: number = priceHistory[priceHistory.length - 1].price;
  firstPrice: number = priceHistory[0].price;
  change: number = this.lastPrice - this.firstPrice;
  changePct: string = ((this.change / this.firstPrice) * 100).toFixed(2);

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.cdr.detectChanges();
  }

  ngAfterViewInit(): void {
    this.drawChart();
  }

  selectTicker(symbol: string): void {
    this.ticker = symbol;
    setTimeout(() => this.drawChart(), 0);
  }

  setSide(s: string): void {
    this.side = s;
  }

  submitOrder(): void {
    this.submitted = true;
    setTimeout(() => {
      this.submitted = false;
    }, 2500);
  }

  getPnlFormatted(pnl: number): string {
    return (pnl >= 0 ? '+' : '') + fmt(pnl);
  }

  getPnlPercent(pnl: number, avgCost: number): string {
    const shares = pnl > 0 ? 1 : -1;
    const percentChange = ((pnl / (avgCost * shares)) * 100);
    return (pnl >= 0 ? '+' : '') + percentChange.toFixed(2);
  }

  drawChart(): void {
    const canvas = this.priceChart.nativeElement as HTMLCanvasElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d') as CanvasRenderingContext2D;
    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;

    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const height = rect.height;
    const padding = 50;
    const chartWidth = width - padding * 2;
    const chartHeight = height - padding * 2;

    ctx.fillStyle = '#0d1225';
    ctx.fillRect(0, 0, width, height);

    const prices = this.priceHistory.map(d => d.price);
    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);
    const priceRange = maxPrice - minPrice || 1;

    // Draw grid lines
    ctx.strokeStyle = '#1e2d45';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = padding + (chartHeight / 4) * i;
      ctx.beginPath();
      ctx.moveTo(padding, y);
      ctx.lineTo(width - padding, y);
      ctx.stroke();

      const price = maxPrice - (priceRange / 4) * i;
      ctx.fillStyle = '#64748b';
      ctx.font = '10px monospace';
      ctx.textAlign = 'right';
      ctx.fillText('$' + price.toFixed(0), width - padding + 10, y + 3);
    }

    // Draw area under curve
    ctx.fillStyle = 'rgba(0, 212, 170, 0.1)';
    ctx.beginPath();
    ctx.moveTo(padding, height - padding);

    for (let i = 0; i < this.priceHistory.length; i++) {
      const x = padding + (chartWidth / (this.priceHistory.length - 1)) * i;
      const y = height - padding - ((this.priceHistory[i].price - minPrice) / priceRange) * chartHeight;
      i === 0 ? ctx.lineTo(x, y) : ctx.lineTo(x, y);
    }

    ctx.lineTo(width - padding, height - padding);
    ctx.fill();

    // Draw line
    ctx.strokeStyle = '#00d4aa';
    ctx.lineWidth = 2;
    ctx.beginPath();
    for (let i = 0; i < this.priceHistory.length; i++) {
      const x = padding + (chartWidth / (this.priceHistory.length - 1)) * i;
      const y = height - padding - ((this.priceHistory[i].price - minPrice) / priceRange) * chartHeight;
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Draw reference line at first price
    ctx.strokeStyle = '#1e2d45';
    ctx.setLineDash([3, 3]);
    const refY = height - padding - ((this.firstPrice - minPrice) / priceRange) * chartHeight;
    ctx.beginPath();
    ctx.moveTo(padding, refY);
    ctx.lineTo(width - padding, refY);
    ctx.stroke();
    ctx.setLineDash([]);
  }
}
