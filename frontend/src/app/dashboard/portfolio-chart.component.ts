import { CommonModule } from '@angular/common';
import { Component, computed, effect, input, output, signal } from '@angular/core';
import { boundedPage, pageRows } from '../core/utils/reporting-workflow';
import { ResultPaginationComponent } from '../shared/result-pagination.component';

export type PortfolioHistoryRange = 5 | 30 | -1 | 365 | 0;
export interface PortfolioValuePoint {
  timestamp: number;
  value: number | null;
  unavailable?: readonly string[];
}
export const portfolioRanges = [
  { days: 5, label: '5D' },
  { days: 30, label: '1M' },
  { days: -1, label: 'YTD' },
  { days: 365, label: '1Y' },
  { days: 0, label: 'ALL' },
] as const;
let chartSequence = 0;

@Component({
  selector: 'app-portfolio-chart',
  standalone: true,
  imports: [CommonModule, ResultPaginationComponent],
  templateUrl: './portfolio-chart.component.html',
  styleUrl: './portfolio-chart.component.scss',
})
export class PortfolioChartComponent {
  readonly value = input<number | null>(null);
  readonly currency = input('GBP');
  readonly valuationLoading = input(false);
  readonly valuationNote = input('');
  readonly points = input<readonly PortfolioValuePoint[]>([]);
  readonly requestedRange = input<PortfolioHistoryRange>(5);
  readonly appliedRange = input<PortfolioHistoryRange>(5);
  readonly loading = input(false);
  readonly failed = input(false);
  readonly rangeSelected = output<PortfolioHistoryRange>();
  readonly retryHistory = output<void>();
  readonly retryValuation = output<void>();
  readonly ranges = portfolioRanges;
  readonly detailId = 'portfolio-point-detail-' + ++chartSequence;
  readonly selectedIndex = signal<number | null>(null);
  readonly focused = signal(false);
  readonly page = signal(1);
  readonly pageSize = signal(10);
  private touchPinned = false;
  readonly selectedPoint = computed(() => this.points()[this.selectedIndex() ?? -1] ?? null);
  readonly valuedPoints = computed(() => this.points().filter((point) => point.value !== null));
  readonly maximum = computed(() =>
    Math.max(1, ...this.valuedPoints().map((point) => point.value!)),
  );
  readonly minimum = computed(() =>
    Math.min(0, ...this.valuedPoints().map((point) => point.value!)),
  );
  readonly labels = computed(() =>
    Array.from(
      { length: 5 },
      (_, i) => this.maximum() - ((this.maximum() - this.minimum()) * i) / 4,
    ),
  );
  readonly dateTicks = computed(() => {
    const count = this.points().length;
    if (!count) return [];
    return [...new Set([0, Math.floor((count - 1) / 2), count - 1])].filter((i) => i >= 0);
  });
  readonly segments = computed(() => {
    const result: string[] = [];
    let segment: string[] = [];
    let previous = -Infinity;
    this.points().forEach((point, i) => {
      if (point.value === null || point.timestamp - previous > 86_400_000) {
        if (segment.length > 1) result.push(segment.join(' '));
        segment = [];
      }
      if (point.value !== null) segment.push(`${this.x(i)},${this.y(point.value)}`);
      previous = point.timestamp;
    });
    if (segment.length > 1) result.push(segment.join(' '));
    return result;
  });
  readonly tableRows = computed(() => pageRows([...this.points()], this.page(), this.pageSize()));

  constructor() {
    effect(() => {
      this.points();
      this.selectedIndex.set(null);
      this.page.set(1);
      this.touchPinned = false;
    });
  }

  rangeLabel(days: PortfolioHistoryRange): string {
    return this.ranges.find((range) => range.days === days)?.label ?? '5D';
  }
  money(value: number | null, compact = false): string {
    if (value === null) return 'Unavailable';
    return new Intl.NumberFormat('en-GB', {
      style: 'currency',
      currency: this.currency(),
      notation: compact ? 'compact' : 'standard',
      maximumFractionDigits: compact ? 1 : 2,
    }).format(value);
  }
  date(timestamp: number): string {
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC',
    }).format(timestamp);
  }
  x(index: number): number {
    const points = this.points();
    if (points.length <= 1) return 400;
    const start = points[0].timestamp;
    const end = points[points.length - 1].timestamp;
    return end === start ? 400 : ((points[index].timestamp - start) / (end - start)) * 800;
  }
  y(value: number): number {
    return 190 - ((value - this.minimum()) / (this.maximum() - this.minimum())) * 180;
  }

  focusChart(): void {
    this.focused.set(true);
    if (this.selectedIndex() === null && this.points().length) this.selectedIndex.set(0);
  }
  blurChart(): void {
    this.focused.set(false);
    if (!this.touchPinned) this.selectedIndex.set(null);
  }
  keydown(event: KeyboardEvent): void {
    const count = this.points().length;
    if (!count) return;
    let index = this.selectedIndex() ?? 0;
    if (event.key === 'ArrowRight') index = Math.min(count - 1, index + 1);
    else if (event.key === 'ArrowLeft') index = Math.max(0, index - 1);
    else if (event.key === 'Home') index = 0;
    else if (event.key === 'End') index = count - 1;
    else if (event.key === 'Escape') {
      this.selectedIndex.set(null);
      this.touchPinned = false;
      event.preventDefault();
      return;
    } else return;
    event.preventDefault();
    this.selectedIndex.set(index);
  }
  pointer(event: PointerEvent): void {
    if (event.type === 'pointerdown') this.touchPinned = event.pointerType !== 'mouse';
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (!this.points().length || bounds.width <= 0) return;
    const x = Math.max(0, Math.min(800, ((event.clientX - bounds.left) / bounds.width) * 800));
    let nearest = 0;
    for (let i = 1; i < this.points().length; i++)
      if (Math.abs(this.x(i) - x) < Math.abs(this.x(nearest) - x)) nearest = i;
    this.selectedIndex.set(nearest);
  }
  leave(): void {
    if (!this.focused() && !this.touchPinned) this.selectedIndex.set(null);
  }
  changePageSize(size: number): void {
    if ([10, 25, 50].includes(size)) {
      this.pageSize.set(size);
      this.page.set(1);
    }
  }
  changePage(page: number): void {
    this.page.set(boundedPage(page, this.points().length, this.pageSize()));
  }
}
