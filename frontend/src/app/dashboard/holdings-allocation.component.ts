import { CommonModule, formatNumber } from '@angular/common';
import {
  Component,
  ElementRef,
  Injector,
  LOCALE_ID,
  ViewChild,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { catchError, forkJoin, map, of, Subscription } from 'rxjs';
import { PositionResponse } from '../core/services/account';
import { FxQuote, FxRateService } from '../core/services/fx-rate';
import { MarketInstrumentResponse } from '../core/services/market-data';
import { AllocationSlice, buildHoldingsAllocation } from './holdings-allocation';

let allocationTooltipId = 0;

@Component({
  selector: 'app-holdings-allocation',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './holdings-allocation.component.html',
  styleUrl: './holdings-allocation.component.scss',
  host: {
    '(document:keydown.escape)': 'dismissTooltip()',
    '(window:resize)': 'dismissTooltip()',
  },
})
export class HoldingsAllocationComponent {
  readonly positions = input<readonly PositionResponse[]>([]);
  readonly instruments = input<readonly MarketInstrumentResponse[]>([]);
  readonly accountCurrency = input('');
  readonly marketLoading = input(false);
  readonly marketRefreshFailed = input(false);
  readonly selectionDisabled = input(false);
  readonly now = input(Date.now());
  readonly instrumentSelected = output<PositionResponse>();
  readonly refreshPrices = output<void>();
  private readonly fxService = inject(FxRateService);
  private readonly locale = inject(LOCALE_ID);
  private readonly injector = inject(Injector);
  @ViewChild('allocationBody') private allocationBody?: ElementRef<HTMLElement>;
  @ViewChild('pieStage') private pieStage?: ElementRef<HTMLElement>;
  @ViewChild('pieTooltip') private pieTooltip?: ElementRef<HTMLElement>;
  readonly tooltipId = 'holdings-allocation-tooltip-' + ++allocationTooltipId;
  private readonly pointerSliceId = signal<number | null>(null);
  private readonly focusedSliceId = signal<number | null>(null);
  private pointerAnchor: { x: number; y: number } | null = null;
  readonly tooltipPosition = signal({ x: 8, y: 8 });
  private readonly fxRetry = signal(0);
  readonly rates = signal<Record<string, FxQuote>>({});
  readonly fxLoading = signal(false);
  readonly fxFailed = signal(false);
  readonly allocation = computed(() =>
    buildHoldingsAllocation(
      this.positions(),
      this.instruments(),
      this.accountCurrency(),
      this.rates(),
      this.now(),
    ),
  );
  readonly tooltipSlice = computed(() => {
    const slices = this.allocation().slices;
    return (
      slices.find((slice) => slice.instrumentId === this.pointerSliceId()) ??
      slices.find((slice) => slice.instrumentId === this.focusedSliceId()) ??
      null
    );
  });
  readonly fxAsOf = computed(
    () =>
      Object.values(this.rates())
        .map((quote) => quote.asOf)
        .sort()[0],
  );
  // Price polls do not refetch FX when the set of required currency pairs is unchanged.
  private readonly fxKey = computed(() => {
    const heldIds = new Set(
      this.positions()
        .filter((p) => p.quantity > 0)
        .map((p) => p.instrumentId),
    );
    const currencies = [
      ...new Set(
        this.instruments()
          .filter((i) => heldIds.has(i.instrumentId) && i.currency !== this.accountCurrency())
          .map((i) => i.currency),
      ),
    ].sort();
    return JSON.stringify([this.accountCurrency(), currencies]);
  });

  constructor() {
    effect((onCleanup) => {
      const [to, currencies] = JSON.parse(this.fxKey()) as [string, string[]];
      this.fxRetry();
      this.rates.set({});
      this.fxFailed.set(false);
      this.fxLoading.set(false);
      if (!to || currencies.length === 0) return;
      let request: Subscription | undefined;
      const load = () => {
        request?.unsubscribe();
        this.fxLoading.set(true);
        request = forkJoin(
          currencies.map((from) =>
            this.fxService.quote(from, to).pipe(
              map((quote) => ({ from, quote })),
              catchError(() => of({ from, quote: null })),
            ),
          ),
        ).subscribe((results) => {
          const rates: Record<string, FxQuote> = {};
          for (const { from, quote } of results) {
            if (
              quote &&
              quote.from === from &&
              quote.to === to &&
              Number.isFinite(quote.rate) &&
              quote.rate > 0
            )
              rates[from] = quote;
          }
          this.rates.set(rates);
          this.fxFailed.set(Object.keys(rates).length !== currencies.length);
          this.fxLoading.set(false);
        });
      };
      load();
      const timer = window.setInterval(load, 60_000);
      onCleanup(() => {
        window.clearInterval(timer);
        request?.unsubscribe();
      });
    });
  }

  showPointerTooltip(instrumentId: number, event: PointerEvent): void {
    this.pointerSliceId.set(instrumentId);
    this.pointerAnchor = { x: event.clientX, y: event.clientY };
    this.positionTooltip();
    this.positionAfterRender();
  }

  movePointerTooltip(event: PointerEvent): void {
    if (this.pointerSliceId() === null) return;
    this.pointerAnchor = { x: event.clientX, y: event.clientY };
    this.positionTooltip();
  }

  clearPointerTooltip(): void {
    this.pointerSliceId.set(null);
    this.pointerAnchor = null;
    this.positionAfterRender();
  }

  showFocusTooltip(instrumentId: number): void {
    this.focusedSliceId.set(instrumentId);
    this.positionAfterRender();
  }

  clearFocusTooltip(): void {
    this.focusedSliceId.set(null);
  }

  dismissTooltip(): void {
    this.pointerSliceId.set(null);
    this.focusedSliceId.set(null);
    this.pointerAnchor = null;
  }

  private positionAfterRender(): void {
    afterNextRender(() => this.positionTooltip(), { injector: this.injector });
  }

  private positionTooltip(): void {
    if (!this.tooltipSlice()) return;
    const stage = this.allocationBody?.nativeElement.getBoundingClientRect();
    if (!stage || stage.width <= 0 || stage.height <= 0) return;
    const box = this.pieTooltip?.nativeElement.getBoundingClientRect();
    const pie = this.pieStage?.nativeElement.getBoundingClientRect();
    const width = box?.width || Math.min(220, stage.width - 16);
    const height = box?.height || Math.min(160, stage.height - 16);
    const anchorX = this.pointerAnchor
      ? this.pointerAnchor.x - stage.left
      : pie?.width
        ? pie.left + pie.width / 2 - stage.left
        : stage.width / 2;
    const anchorY = this.pointerAnchor
      ? this.pointerAnchor.y - stage.top
      : pie?.height
        ? pie.top + pie.height / 2 - stage.top
        : stage.height / 2;
    let x = anchorX + 12;
    let y = anchorY + 12;
    if (x + width > stage.width - 8) x = anchorX - width - 12;
    if (y + height > stage.height - 8) y = anchorY - height - 12;
    this.tooltipPosition.set({
      x: Math.max(8, Math.min(x, stage.width - width - 8)),
      y: Math.max(8, Math.min(y, stage.height - height - 8)),
    });
  }

  formatValue(value: number): string {
    // Use account-currency amounts, retaining useful precision for small holdings.
    const digits = value > 0 && value < 0.01 ? '1.2-6' : '1.2-2';
    const amount =
      value > 0 && value < 0.000001
        ? '<' + formatNumber(0.000001, this.locale, '1.2-6')
        : formatNumber(value, this.locale, digits);
    return `${this.accountCurrency()} ${amount}`;
  }

  formatShare(share: number): string {
    return share > 0 && share < 0.1
      ? '<' + formatNumber(0.1, this.locale, '1.1-1') + '%'
      : formatNumber(share, this.locale, '1.1-1') + '%';
  }

  sliceTooltip(slice: AllocationSlice): string {
    return `${slice.symbol} — ${this.formatValue(slice.value)} · ${this.formatShare(slice.share)}`;
  }

  retryFx(): void {
    if (!this.fxLoading()) this.fxRetry.update((value) => value + 1);
  }
}
