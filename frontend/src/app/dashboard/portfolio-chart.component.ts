import { CommonModule } from '@angular/common';
import {
  afterRenderEffect,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { boundedPage, pageRows } from '../core/utils/reporting-workflow';
import { ResultPaginationComponent } from '../shared/result-pagination.component';
import { LIVE_PORTFOLIO_WINDOW_MS } from './portfolio-live-history';

export type PortfolioHistoryRange = 5 | 30 | -1 | 365 | 0;
export interface PortfolioValuePoint {
  timestamp: number;
  value: number | null;
  unavailable?: readonly string[];
  breakBefore?: boolean;
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
  readonly liveEnabled = input(false);
  readonly livePoints = input<readonly PortfolioValuePoint[]>([]);
  readonly now = input(Date.now());
  readonly view = signal<'live' | 'daily'>('live');
  readonly isLive = computed(() => this.liveEnabled() && this.view() === 'live');
  readonly displayedPoints = computed(() =>
    this.isLive()
      ? this.livePoints().filter(
          (point) =>
            point.timestamp >= this.now() - LIVE_PORTFOLIO_WINDOW_MS &&
            point.timestamp <= this.now(),
        )
      : this.points(),
  );
  readonly liveStatus = computed(() => {
    const last = this.displayedPoints().at(-1);
    if (!last) return 'Waiting for live values';
    return last.value !== null && this.now() - last.timestamp <= 10_000 ? 'Live' : 'Updates paused';
  });
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
  readonly pointerAnchor = signal<{ x: number; y: number } | null>(null);
  private readonly tooltip = viewChild<ElementRef<HTMLElement>>('pointTooltip');
  readonly page = signal(1);
  readonly pageSize = signal(10);
  private touchPinned = false;
  readonly selectedPoint = computed(
    () => this.displayedPoints()[this.selectedIndex() ?? -1] ?? null,
  );
  readonly selectedChange = computed(() => {
    const index = this.selectedIndex();
    if (index === null || index < 1) return null;
    const point = this.displayedPoints()[index];
    const previous = this.displayedPoints()[index - 1];
    if (
      !point ||
      !previous ||
      point.value === null ||
      previous.value === null ||
      point.breakBefore ||
      point.timestamp - previous.timestamp > (this.isLive() ? 10_000 : 86_400_000)
    )
      return null;
    const amount = point.value - previous.value;
    return { amount, percent: previous.value > 0 ? (amount / previous.value) * 100 : null };
  });
  readonly valuedPoints = computed(() =>
    this.displayedPoints().filter((point) => point.value !== null),
  );
  readonly bounds = computed(() => {
    const values = this.valuedPoints().map((point) => point.value!);
    if (!values.length) return { low: 0, high: 1 };
    const low = Math.min(...values),
      high = Math.max(...values);
    const padding = Math.max((high - low) * 0.15, high * 0.00001, 0.01);
    return { low: Math.max(0, low - padding), high: high + padding };
  });
  readonly maximum = computed(() => this.bounds().high);
  readonly minimum = computed(() => this.bounds().low);
  readonly labels = computed(() =>
    Array.from(
      { length: 5 },
      (_, i) => this.maximum() - ((this.maximum() - this.minimum()) * i) / 4,
    ),
  );
  readonly dateTicks = computed(() => {
    const count = this.displayedPoints().length;
    if (!count) return [];
    return [...new Set([0, Math.floor((count - 1) / 2), count - 1])].filter((i) => i >= 0);
  });
  readonly segments = computed(() => {
    const result: string[] = [];
    let segment: string[] = [];
    let previous = -Infinity;
    this.displayedPoints().forEach((point, i) => {
      if (
        point.value === null ||
        point.breakBefore ||
        point.timestamp - previous > (this.isLive() ? 10_000 : 86_400_000)
      ) {
        if (segment.length > 1) result.push(segment.join(' '));
        segment = [];
      }
      if (point.value !== null) segment.push(`${this.x(i)},${this.y(point.value)}`);
      previous = point.timestamp;
    });
    if (segment.length > 1) result.push(segment.join(' '));
    return result;
  });
  readonly tableRows = computed(() =>
    pageRows([...this.displayedPoints()], this.page(), this.pageSize()),
  );

  constructor() {
    afterRenderEffect({
      earlyRead: () => {
        const anchor = this.pointerAnchor();
        const tooltip = this.tooltip()?.nativeElement;
        this.selectedPoint();
        if (!anchor || !tooltip) return null;
        const { width, height } = tooltip.getBoundingClientRect();
        const gap = 16,
          margin = 12;
        const x =
          anchor.x + gap + width <= window.innerWidth - margin
            ? anchor.x + gap
            : anchor.x - width - gap;
        const y =
          anchor.y + gap + height <= window.innerHeight - margin
            ? anchor.y + gap
            : anchor.y - height - gap;
        return {
          tooltip,
          x: Math.max(margin, Math.min(x, window.innerWidth - width - margin)),
          y: Math.max(margin, Math.min(y, window.innerHeight - height - margin)),
        };
      },
      write: (placement) => {
        const position = placement();
        if (!position) return;
        position.tooltip.style.left = `${position.x}px`;
        position.tooltip.style.top = `${position.y}px`;
        position.tooltip.style.visibility = 'visible';
      },
    });
    const dismiss = () => this.dismissTooltip();
    window.addEventListener('scroll', dismiss, true);
    window.addEventListener('resize', dismiss);
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('scroll', dismiss, true);
      window.removeEventListener('resize', dismiss);
    });
    effect(() => {
      const points = this.displayedPoints();
      if (this.isLive()) {
        // Preserve an inspected timestamp while fresh observations arrive.
        const timestamp = this.selectionTimestamp;
        let index: number | null = null;
        if (timestamp !== null && points.length) {
          index = 0;
          for (let i = 1; i < points.length; i++)
            if (
              Math.abs(points[i].timestamp - timestamp) <
              Math.abs(points[index].timestamp - timestamp)
            )
              index = i;
        }
        this.selectedIndex.set(index);
      } else {
        this.selectedIndex.set(null);
        this.page.set(1);
        this.touchPinned = false;
      }
    });
  }

  private selectionTimestamp: number | null = null;
  setView(view: 'live' | 'daily', range?: PortfolioHistoryRange): void {
    this.dismissTooltip();
    this.view.set(view);
    this.selectedIndex.set(null);
    this.selectionTimestamp = null;
    this.page.set(1);
    this.touchPinned = false;
    if (range !== undefined) this.rangeSelected.emit(range);
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
  changeMoney(value: number): string {
    return new Intl.NumberFormat('en-GB', {
      style: 'currency',
      currency: this.currency(),
      signDisplay: 'exceptZero',
      maximumFractionDigits: 2,
    }).format(value);
  }
  changePercent(value: number): string {
    return (
      new Intl.NumberFormat('en-GB', {
        signDisplay: 'exceptZero',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value) + '%'
    );
  }
  dismissTooltip(): void {
    this.pointerAnchor.set(null);
  }
  date(timestamp: number): string {
    return new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      ...(this.isLive()
        ? { hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' as const }
        : {}),
      timeZone: 'UTC',
    }).format(timestamp);
  }
  tickLabel(timestamp: number): string {
    return this.isLive()
      ? new Intl.DateTimeFormat('en-GB', {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hourCycle: 'h23',
          timeZone: 'UTC',
        }).format(timestamp)
      : this.date(timestamp);
  }
  x(index: number): number {
    const points = this.displayedPoints();
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
    if (this.selectedIndex() === null && this.displayedPoints().length) {
      this.selectedIndex.set(this.isLive() ? this.displayedPoints().length - 1 : 0);
      this.selectionTimestamp = this.selectedPoint()?.timestamp ?? null;
    }
  }
  blurChart(): void {
    this.dismissTooltip();
    this.focused.set(false);
    if (!this.touchPinned) {
      this.selectedIndex.set(null);
      this.selectionTimestamp = null;
    }
  }
  keydown(event: KeyboardEvent): void {
    this.dismissTooltip();
    const count = this.displayedPoints().length;
    if (!count) return;
    let index = this.selectedIndex() ?? 0;
    if (event.key === 'ArrowRight') index = Math.min(count - 1, index + 1);
    else if (event.key === 'ArrowLeft') index = Math.max(0, index - 1);
    else if (event.key === 'Home') index = 0;
    else if (event.key === 'End') index = count - 1;
    else if (event.key === 'Escape') {
      this.selectedIndex.set(null);
      this.selectionTimestamp = null;
      this.touchPinned = false;
      event.preventDefault();
      return;
    } else return;
    event.preventDefault();
    this.selectedIndex.set(index);
    this.selectionTimestamp = this.selectedPoint()?.timestamp ?? null;
  }
  pointer(event: PointerEvent): void {
    if (event.type === 'pointerdown') this.touchPinned = event.pointerType !== 'mouse';
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (!this.displayedPoints().length || bounds.width <= 0) return;
    const x = Math.max(0, Math.min(800, ((event.clientX - bounds.left) / bounds.width) * 800));
    let nearest = 0;
    for (let i = 1; i < this.displayedPoints().length; i++)
      if (Math.abs(this.x(i) - x) < Math.abs(this.x(nearest) - x)) nearest = i;
    this.selectedIndex.set(nearest);
    this.selectionTimestamp = this.selectedPoint()?.timestamp ?? null;
    this.pointerAnchor.set({ x: event.clientX, y: event.clientY });
  }
  leave(): void {
    this.dismissTooltip();
    if (!this.focused() && !this.touchPinned) {
      this.selectedIndex.set(null);
      this.selectionTimestamp = null;
    }
  }
  changePageSize(size: number): void {
    if ([10, 25, 50].includes(size)) {
      this.pageSize.set(size);
      this.page.set(1);
    }
  }
  changePage(page: number): void {
    this.page.set(boundedPage(page, this.displayedPoints().length, this.pageSize()));
  }
}
