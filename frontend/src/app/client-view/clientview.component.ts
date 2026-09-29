import { Component, ViewChild, ElementRef, OnInit, AfterViewInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { priceHistory, positions, orderHistory, sectorAllocation, portfolioHistory } from '../data/mock-data';

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

interface Sector {
  sector: string;
  value: number;
  pct: number;
}

interface PortfolioData {
  date: string;
  value: number;
}

function fmt(n: number, compact: boolean = false): string {
  if (compact && n >= 1000) return `$${(n / 1000).toFixed(1)}K`;
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
}

@Component({
  selector: 'app-clientview',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './clientview.component.html',
  styleUrls: ['./clientview.component.scss'],
})
export class ClientViewComponent implements OnInit, AfterViewInit {
  @ViewChild('portfolioChart') portfolioChart!: ElementRef<HTMLCanvasElement>;
  @ViewChild('sectorPie') sectorPie!: ElementRef<HTMLCanvasElement>;

  positions: Position[] = positions;
  orderHistory: Order[] = orderHistory;
  sectorAllocation: Sector[] = sectorAllocation;
  portfolioHistory: PortfolioData[] = portfolioHistory;

  totalEquity: number = 0;
  totalCost: number = 0;
  totalPl: number = 0;
  cashBalance: number = 36447.20;
  portfolioValue: number = 0;
  returnPct: number = 0;

  sectorColors: string[] = ['#00d4aa', '#06b6d4', '#818cf8', '#f59e0b', '#64748b'];

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.calculatePortfolioMetrics();
    this.cdr.detectChanges();
  }

  ngAfterViewInit(): void {
    this.drawPortfolioChart();
    this.drawSectorPie();
  }

  calculatePortfolioMetrics(): void {
    this.totalEquity = this.positions.reduce((a, p) => a + p.currentPrice * p.shares, 0);
    this.totalCost = this.positions.reduce((a, p) => a + p.avgCost * p.shares, 0);
    this.totalPl = this.totalEquity - this.totalCost;
    this.portfolioValue = this.totalEquity + this.cashBalance;
    this.returnPct = this.totalCost > 0 ? (this.totalPl / this.totalCost) * 100 : 0;
  }

  fmt(n: number): string {
    return fmt(n, false);
  }

  getPositionPL(p: Position): number {
    return (p.currentPrice - p.avgCost) * p.shares;
  }

  getPositionReturn(p: Position): number {
    return ((p.currentPrice - p.avgCost) / p.avgCost) * 100;
  }

  drawPortfolioChart(): void {
    const canvas = this.portfolioChart.nativeElement as HTMLCanvasElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d') as CanvasRenderingContext2D;
    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;

    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const height = rect.height;
    const padding = 40;
    const chartWidth = width - padding * 2;
    const chartHeight = height - padding * 2;

    ctx.fillStyle = '#0d1225';
    ctx.fillRect(0, 0, width, height);

    const values = this.portfolioHistory.map(d => d.value);
    const minValue = Math.min(...values);
    const maxValue = Math.max(...values);
    const valueRange = maxValue - minValue || 1;

    // Draw grid lines
    ctx.strokeStyle = '#1e2d45';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = padding + (chartHeight / 4) * i;
      ctx.beginPath();
      ctx.moveTo(padding, y);
      ctx.lineTo(width - padding, y);
      ctx.stroke();

      const val = maxValue - (valueRange / 4) * i;
      ctx.fillStyle = '#64748b';
      ctx.font = '10px monospace';
      ctx.textAlign = 'right';
      ctx.fillText('$' + (val / 1000).toFixed(0) + 'K', width - padding + 10, y + 3);
    }

    // Draw area under curve
    ctx.fillStyle = 'rgba(0, 212, 170, 0.1)';
    ctx.beginPath();
    ctx.moveTo(padding, height - padding);

    for (let i = 0; i < this.portfolioHistory.length; i++) {
      const x = padding + (chartWidth / (this.portfolioHistory.length - 1)) * i;
      const y = height - padding - ((this.portfolioHistory[i].value - minValue) / valueRange) * chartHeight;
      i === 0 ? ctx.lineTo(x, y) : ctx.lineTo(x, y);
    }

    ctx.lineTo(width - padding, height - padding);
    ctx.fill();

    // Draw line
    ctx.strokeStyle = '#00d4aa';
    ctx.lineWidth = 2;
    ctx.beginPath();
    for (let i = 0; i < this.portfolioHistory.length; i++) {
      const x = padding + (chartWidth / (this.portfolioHistory.length - 1)) * i;
      const y = height - padding - ((this.portfolioHistory[i].value - minValue) / valueRange) * chartHeight;
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
    ctx.stroke();
  }

  drawSectorPie(): void {
    const canvas = this.sectorPie.nativeElement as HTMLCanvasElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d') as CanvasRenderingContext2D;
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;

    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const height = rect.height;
    const centerX = width / 2;
    const centerY = height / 2;
    const outerRadius = Math.min(width, height) / 2 - 10;
    const innerRadius = outerRadius * 0.6;

    // Clear background
    ctx.fillStyle = '#0d1225';
    ctx.fillRect(0, 0, width, height);

    // Draw each sector
    let currentAngle = -Math.PI / 2;

    this.sectorAllocation.forEach((sector, i) => {
      const percentValue = sector.pct || 0;
      const sliceAngle = (percentValue / 100) * 2 * Math.PI;

      // Draw donut slice
      ctx.fillStyle = this.sectorColors[i % this.sectorColors.length];
      ctx.beginPath();
      ctx.arc(centerX, centerY, outerRadius, currentAngle, currentAngle + sliceAngle);
      ctx.lineTo(
        centerX + innerRadius * Math.cos(currentAngle + sliceAngle),
        centerY + innerRadius * Math.sin(currentAngle + sliceAngle)
      );
      ctx.arc(centerX, centerY, innerRadius, currentAngle + sliceAngle, currentAngle, true);
      ctx.closePath();
      ctx.fill();

      currentAngle += sliceAngle;
    });
  }
}
