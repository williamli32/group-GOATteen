import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MarketInstrumentResponse } from '../core/services/market-data';
import {
  FRESH_PRICE_MS,
  InstrumentPriceHistory,
  InstrumentPriceHistoryService,
  InstrumentPricePoint,
  PRICE_WINDOW_MS,
  rollingPricePoints,
  samplePricePoints,
} from './instrument-price-history';

let chartSequence = 0;

@Component({
  selector: 'app-instrument-price-chart',
  standalone: true,
  templateUrl: './instrument-price-chart.component.html',
  styleUrl: './instrument-price-chart.component.scss',
})
export class InstrumentPriceChartComponent {
  readonly instrument = input<MarketInstrumentResponse | null>(null);
  readonly active = input(true);
  readonly now = input(Date.now());
  readonly marketLoading = input(false);
  readonly refreshFailed = input(false);
  readonly refreshPrices = output<void>();
  readonly history = signal<InstrumentPriceHistory | null>(null);
  readonly view = signal<'day' | 'live'>('day');
  readonly selectedTime = signal<number | null>(null);
  readonly focused = signal(false);
  readonly id = 'instrument-price-' + ++chartSequence;
  private readonly histories = inject(InstrumentPriceHistoryService);
  private currentKey = '';
  private touchPinned = false;
  readonly windowStart = computed(() =>
    this.view() === 'day'
      ? this.now() - PRICE_WINDOW_MS
      : Math.max(
          this.now() - 3_600_000,
          Math.min(this.history()?.firstLive.timestamp ?? this.now(), this.now() - 60_000),
        ),
  );
  readonly points = computed(() =>
    rollingPricePoints(
      (this.history()?.points ?? []).filter(
        (point) => this.view() === 'day' || point.source === 'live',
      ),
      this.now(),
      this.now() - this.windowStart(),
    ),
  );
  readonly livePoints = computed(() =>
    samplePricePoints(
      this.points().filter((point) => point.source === 'live'),
      360,
    ),
  );
  readonly previewPoints = computed(() => {
    const generated = this.points().filter((point) => point.source === 'illustrative');
    return generated.length
      ? [...samplePricePoints(generated, 128), ...this.livePoints().slice(0, 1)]
      : [];
  });
  readonly inspectablePoints = computed(() =>
    [
      ...this.previewPoints().filter((point) => point.source === 'illustrative'),
      ...this.livePoints(),
    ].filter((point) => point.timestamp >= this.windowStart()),
  );
  readonly bounds = computed(() => {
    const prices = this.points().map((point) => point.price);
    const low = Math.min(...prices),
      high = Math.max(...prices);
    if (!Number.isFinite(low) || !Number.isFinite(high)) return { low: 0, high: 1 };
    const margin = Math.max((high - low) * 0.15, high * 0.001);
    return { low: Math.max(0, low - margin), high: high + margin };
  });
  readonly ticks = computed(() =>
    Array.from(
      { length: 5 },
      (_, i) => this.bounds().high - ((this.bounds().high - this.bounds().low) * i) / 4,
    ),
  );
  readonly timeTicks = computed(() =>
    (this.view() === 'day' ? [0, 0.25, 0.5, 0.75, 1] : [0, 0.5, 1]).map(
      (fraction) => this.windowStart() + fraction * (this.now() - this.windowStart()),
    ),
  );
  readonly previewPath = computed(() => this.path(this.previewPoints()));
  readonly livePath = computed(() => this.path(this.livePoints()));
  readonly liveArea = computed(() => this.path(this.livePoints(), true));
  readonly previewArea = computed(() => this.path(this.previewPoints(), true));
  readonly selectedPoint = computed(() => {
    const time = this.selectedTime();
    if (time === null) return null;
    const points = this.inspectablePoints();
    return points.reduce<InstrumentPricePoint | null>(
      (best, point) =>
        !best || Math.abs(point.timestamp - time) < Math.abs(best.timestamp - time) ? point : best,
      null,
    );
  });
  readonly live = computed(
    () =>
      !!this.history() &&
      !this.refreshFailed() &&
      this.history()!.latest.timestamp <= this.now() &&
      this.now() - this.history()!.latest.timestamp <= FRESH_PRICE_MS,
  );
  readonly liveChange = computed(() => {
    const history = this.history();
    return history
      ? ((history.latest.price - history.firstLive.price) / history.firstLive.price) * 100
      : 0;
  });
  readonly plotLabel = computed(
    () =>
      `${this.instrument()?.symbol ?? 'Instrument'} ${this.view() === 'day' ? '24-hour' : 'live detail'} price chart in ${this.instrument()?.currency ?? ''}. Use Left and Right arrows, Home or End to inspect prices; Escape returns to the latest quote.`,
  );

  constructor() {
    effect(() => {
      const instrument = this.instrument();
      if (!this.active()) return;
      const history = this.histories.observe(instrument, this.now());
      const key = history?.key ?? '';
      if (key !== this.currentKey) {
        this.currentKey = key;
        this.selectedTime.set(null);
        this.touchPinned = false;
        this.view.set('day');
      }
      this.history.set(history);
    });
  }

  money(value: number): string {
    return new Intl.NumberFormat('en-GB', {
      style: 'currency',
      currency: this.instrument()?.currency ?? 'GBP',
      currencyDisplay: 'code',
      minimumFractionDigits: 2,
      maximumFractionDigits: value < 1 ? 8 : value < 10 ? 6 : 4,
    }).format(value);
  }
  axisPrice(value: number): string {
    return new Intl.NumberFormat('en-GB', {
      notation: value >= 10_000 ? 'compact' : 'standard',
      maximumFractionDigits: value < 1 ? 6 : 2,
    }).format(value);
  }
  time(value: number, full = false): string {
    return new Intl.DateTimeFormat('en-GB', {
      ...(full
        ? ({ day: 'numeric', month: 'short', year: 'numeric', second: '2-digit' } as const)
        : {}),
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
      timeZone: 'UTC',
    }).format(value);
  }
  percent(value: number): string {
    return (
      new Intl.NumberFormat('en-GB', {
        signDisplay: 'exceptZero',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value) + '%'
    );
  }
  tickTime(value: number): string {
    return new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      ...(this.view() === 'live' ? ({ second: '2-digit' } as const) : {}),
      hourCycle: 'h23',
      timeZone: 'UTC',
    }).format(value);
  }
  setView(view: 'day' | 'live'): void {
    this.view.set(view);
    this.selectedTime.set(null);
    this.touchPinned = false;
  }
  x(point: InstrumentPricePoint): number {
    return ((point.timestamp - this.windowStart()) / (this.now() - this.windowStart())) * 1000;
  }
  y(point: InstrumentPricePoint): number {
    return (
      15 + ((this.bounds().high - point.price) / (this.bounds().high - this.bounds().low)) * 210
    );
  }
  private path(points: readonly InstrumentPricePoint[], fill = false): string {
    const segments: InstrumentPricePoint[][] = [];
    for (const point of points) {
      if (!segments.length || point.breakBefore) segments.push([]);
      segments[segments.length - 1].push(point);
    }
    return segments
      .filter((segment) => segment.length > 1)
      .map((segment) => {
        const line = segment
          .map(
            (point, i) =>
              `${i === 0 ? 'M' : 'L'}${this.x(point).toFixed(2)},${this.y(point).toFixed(2)}`,
          )
          .join(' ');
        return fill
          ? line +
              ` L${this.x(segment[segment.length - 1]).toFixed(2)},240 L${this.x(segment[0]).toFixed(2)},240 Z`
          : line;
      })
      .join(' ');
  }
  focusChart(): void {
    this.focused.set(true);
  }
  blurChart(): void {
    this.focused.set(false);
    if (!this.touchPinned) this.selectedTime.set(null);
  }
  keydown(event: KeyboardEvent): void {
    const points = this.inspectablePoints();
    if (!points.length) return;
    const selected = this.selectedPoint();
    let index = selected ? points.indexOf(selected) : points.length - 1;
    if (event.key === 'ArrowLeft') index = Math.max(0, index - 1);
    else if (event.key === 'ArrowRight') index = Math.min(points.length - 1, index + 1);
    else if (event.key === 'Home') index = 0;
    else if (event.key === 'End') index = points.length - 1;
    else if (event.key === 'Escape') {
      this.selectedTime.set(null);
      this.touchPinned = false;
      event.preventDefault();
      return;
    } else return;
    event.preventDefault();
    this.selectedTime.set(points[index].timestamp);
  }
  pointer(event: PointerEvent): void {
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (bounds.width <= 0) return;
    if (event.type === 'pointerdown') this.touchPinned = event.pointerType !== 'mouse';
    this.selectedTime.set(
      this.windowStart() +
        Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width)) *
          (this.now() - this.windowStart()),
    );
  }
  leaveChart(): void {
    if (!this.focused() && !this.touchPinned) this.selectedTime.set(null);
  }
}
