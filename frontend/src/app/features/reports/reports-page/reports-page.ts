import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

interface DailyPnL {
  date: string;
  pnl: number;
}

interface VolumeData {
  symbol: string;
  volume: number;
  count: number;
}

interface MonthlyActivity {
  month: string;
  buys: number;
  sells: number;
  volume: number;
}

interface SectorData {
  sector: string;
  pct: number;
  value: number;
}

const MOCK_DAILY_PNL: DailyPnL[] = [
  { date: 'Aug 15', pnl: 3200 },
  { date: 'Aug 16', pnl: -1800 },
  { date: 'Aug 17', pnl: 4500 },
  { date: 'Aug 18', pnl: 2100 },
  { date: 'Aug 19', pnl: -950 },
  { date: 'Aug 20', pnl: 5300 },
  { date: 'Aug 21', pnl: 1200 },
  { date: 'Aug 22', pnl: -2400 },
  { date: 'Aug 23', pnl: 3800 },
  { date: 'Aug 24', pnl: 2900 },
  { date: 'Aug 25', pnl: -1100 },
  { date: 'Aug 26', pnl: 4200 },
  { date: 'Aug 27', pnl: 3500 },
  { date: 'Aug 28', pnl: 2800 },
];

const MOCK_ORDER_HISTORY = [
  { symbol: 'AAPL', total: 15000, status: 'FILLED' },
  { symbol: 'MSFT', total: 22000, status: 'FILLED' },
  { symbol: 'NVDA', total: 28000, status: 'FILLED' },
  { symbol: 'JPM', total: 9000, status: 'FILLED' },
  { symbol: 'V', total: 12500, status: 'FILLED' },
  { symbol: 'XOM', total: 7200, status: 'FILLED' },
  { symbol: 'JNJ', total: 11000, status: 'FILLED' },
  { symbol: 'AAPL', total: 8500, status: 'FILLED' },
  { symbol: 'MSFT', total: 13200, status: 'FILLED' },
  { symbol: 'NVDA', total: 19000, status: 'PARTIAL' },
  { symbol: 'JPM', total: 6800, status: 'FILLED' },
  { symbol: 'V', total: 14300, status: 'FILLED' },
  { symbol: 'XOM', total: 5600, status: 'FILLED' },
  { symbol: 'JNJ', total: 9200, status: 'FILLED' },
  { symbol: 'AAPL', total: 7800, status: 'FILLED' },
  { symbol: 'MSFT', total: 16400, status: 'FILLED' },
];

const MOCK_MONTHLY_ACTIVITY: MonthlyActivity[] = [
  { month: 'Mar', buys: 12, sells: 5, volume: 142000 },
  { month: 'Apr', buys: 18, sells: 9, volume: 198000 },
  { month: 'May', buys: 22, sells: 14, volume: 267000 },
  { month: 'Jun', buys: 15, sells: 11, volume: 183000 },
  { month: 'Jul', buys: 25, sells: 18, volume: 312000 },
  { month: 'Aug', buys: 9, sells: 6, volume: 147000 },
];

const MOCK_SECTOR_ALLOCATION: SectorData[] = [
  { sector: 'Technology', pct: 38, value: 285000 },
  { sector: 'Financials', pct: 22, value: 165000 },
  { sector: 'Healthcare', pct: 18, value: 135000 },
  { sector: 'Energy', pct: 12, value: 90000 },
  { sector: 'Other', pct: 10, value: 75000 },
];

@Component({
  imports: [CommonModule],
  selector: 'app-reports-page',
  styleUrl: './reports-page.scss',
  templateUrl: './reports-page.html',
})
export class ReportsPage {
  period: '7D' | '30D' | 'MTD' | 'YTD' = '30D';
  dailyPnl: DailyPnL[] = MOCK_DAILY_PNL;
  monthlyActivity: MonthlyActivity[] = MOCK_MONTHLY_ACTIVITY;
  sectorData: SectorData[] = MOCK_SECTOR_ALLOCATION;
  protected readonly MOCK_ORDER_HISTORY = MOCK_ORDER_HISTORY;

  get volumeBySymbol(): VolumeData[] {
    const symbols = ['AAPL', 'MSFT', 'NVDA', 'JPM', 'V', 'XOM', 'JNJ'];
    return symbols.map((s) => {
      const orders = MOCK_ORDER_HISTORY.filter((o) => o.symbol === s);
      return {
        symbol: s,
        volume: orders.reduce((a, o) => a + o.total, 0),
        count: orders.length,
      };
    });
  }

  get totalPnl(): number {
    return this.dailyPnl.reduce((a, d) => a + d.pnl, 0);
  }

  get winDays(): number {
    return this.dailyPnl.filter((d) => d.pnl > 0).length;
  }

  get bestDay(): number {
    return Math.max(...this.dailyPnl.map((d) => d.pnl));
  }

  get worstDay(): number {
    return Math.min(...this.dailyPnl.map((d) => d.pnl));
  }

  get bestDayDate(): string {
    return this.dailyPnl.find((d) => d.pnl === this.bestDay)?.date || '';
  }

  get fillRate(): string {
    const filled = MOCK_ORDER_HISTORY.filter((o) => o.status === 'FILLED').length;
    return ((filled / MOCK_ORDER_HISTORY.length) * 100).toFixed(1);
  }

  get filledOrders(): number {
    return MOCK_ORDER_HISTORY.filter((o) => o.status === 'FILLED').length;
  }

  formatShort(n: number): string {
    const abs = Math.abs(n);
    const sign = n < 0 ? '-' : n > 0 ? '+' : '';
    if (abs >= 1000) return `${sign}$${(abs / 1000).toFixed(1)}K`;
    return `${sign}$${abs.toFixed(0)}`;
  }

  formatFull(n: number): string {
    return n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
  }

  setPeriod(p: '7D' | '30D' | 'MTD' | 'YTD'): void {
    this.period = p;
  }

  getChartBarHeight(value: number, maxValue: number): number {
    return (value / maxValue) * 100;
  }

  getChartBarWidth(value: number, maxValue: number): number {
    return (value / maxValue) * 100;
  }

  getSectorPlContrib(sector: SectorData): number {
    return sector.value * (Math.random() * 0.14 - 0.04);
  }

  protected readonly Math = Math;
}
