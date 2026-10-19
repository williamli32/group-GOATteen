import { CommonModule } from '@angular/common';
import { Component, computed, DestroyRef, inject, input, OnInit, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { MarketInstrumentResponse } from '../core/services/market-data';
import { WatchlistService } from '../core/services/watchlist';
import { DailyPriceChangeComponent } from './daily-price-change.component';

@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [CommonModule, FormsModule, DailyPriceChangeComponent],
  templateUrl: './watchlist.component.html',
  styleUrl: './watchlist.component.scss'
})
export class WatchlistComponent implements OnInit {
  readonly instruments = input<MarketInstrumentResponse[]>([]);
  readonly marketLoading = input(false);
  readonly marketError = input('');
  readonly selectedInstrumentId = input<number | null>(null);
  readonly selectionDisabled = input(false);
  readonly instrumentSelected = output<MarketInstrumentResponse>();
  readonly savedInstruments = signal<MarketInstrumentResponse[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly pendingId = signal<number | null>(null);
  readonly error = signal('');
  readonly success = signal('');
  addId = '';

  private readonly service = inject(WatchlistService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly marketById = computed(() =>
    new Map(this.instruments().map(instrument => [instrument.instrumentId, instrument]))
  );

  readonly watchedInstruments = computed(() => this.savedInstruments()
    .map(saved => {
      const live = this.marketById().get(saved.instrumentId);
      if (!live) return saved;
      const previous = saved.latestQuote;
      const current = live.latestQuote;
      // The list and market requests can finish in either order. Preserve a
      // newer saved quote until the market refresh catches up.
      if (previous && current && (previous.quotedAt > current.quotedAt
        || (previous.quotedAt === current.quotedAt && previous.quoteId > current.quoteId))) {
        // Keep the percentage paired with its quote. Older market data must
        // not supply a percentage for a newer watchlist price.
        return { ...live, latestQuote: previous, dayChange: saved.dayChange ?? null };
      }
      return live;
    })
    .sort((a, b) => a.symbol.localeCompare(b.symbol) || a.exchange.localeCompare(b.exchange))
  );

  readonly availableInstruments = computed(() => {
    const savedIds = new Set(this.savedInstruments().map(instrument => instrument.instrumentId));
    return this.instruments().filter(instrument => instrument.tradable && !savedIds.has(instrument.instrumentId));
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    if (this.loading() || this.pendingId() !== null) return;
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.service.getWatchlist().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: instruments => {
        this.savedInstruments.set(instruments);
        this.loaded.set(true);
      },
      error: () => this.error.set('Your watchlist could not be loaded. Refresh to try again.')
    });
  }

  addSelected(): void {
    if (!this.loaded() || this.loading() || this.pendingId() !== null) return;
    const id = Number(this.addId);
    const instrument = this.availableInstruments().find(item => item.instrumentId === id);
    if (!instrument) {
      this.error.set('Choose an available instrument to add.');
      return;
    }

    this.beginChange(id);
    this.service.add(id).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pendingId.set(null))
    ).subscribe({
      next: () => {
        this.savedInstruments.update(items => items.some(item => item.instrumentId === id)
          ? items : [...items, instrument]);
        this.addId = '';
        this.success.set(instrument.symbol + ' added to your watchlist.');
      },
      error: () => this.error.set(
        'We could not confirm this addition. Refresh your watchlist or retry; it will only be saved once.')
    });
  }

  remove(instrument: MarketInstrumentResponse): void {
    if (this.loading() || this.pendingId() !== null) return;
    this.beginChange(instrument.instrumentId);
    this.service.remove(instrument.instrumentId).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pendingId.set(null))
    ).subscribe({
      next: () => {
        this.savedInstruments.update(items => items.filter(item => item.instrumentId !== instrument.instrumentId));
        this.success.set(instrument.symbol + ' removed from your watchlist.');
      },
      error: () => this.error.set('We could not confirm this removal. Refresh your watchlist or retry.')
    });
  }

  canSelect(instrument: MarketInstrumentResponse): boolean {
    const live = this.marketById().get(instrument.instrumentId);
    return !this.selectionDisabled() && !this.marketLoading() && !this.marketError()
      && !!live?.tradable && !!live.latestQuote;
  }

  select(instrument: MarketInstrumentResponse): void {
    if (this.canSelect(instrument)) {
      const current = this.watchedInstruments().find(item => item.instrumentId === instrument.instrumentId);
      if (current) this.instrumentSelected.emit(current);
    }
  }

  private beginChange(id: number): void {
    this.pendingId.set(id);
    this.error.set('');
    this.success.set('');
  }
}
