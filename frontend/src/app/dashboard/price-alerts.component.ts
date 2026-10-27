import { CommonModule } from '@angular/common';
import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, exhaustMap, finalize, forkJoin, interval, map, of } from 'rxjs';
import { MarketInstrumentResponse } from '../core/services/market-data';
import {
  AlertDirection, PriceAlert, PriceAlertChange, PriceAlertsService, PriceNotice
} from '../core/services/price-alerts';

@Component({
  selector: 'app-price-alerts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './price-alerts.component.html',
  styleUrl: './price-alerts.component.scss'
})
export class PriceAlertsComponent implements OnInit {
  readonly instruments = input<MarketInstrumentResponse[]>([]);
  readonly alerts = signal<PriceAlert[]>([]);
  readonly notices = signal<PriceNotice[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly info = signal('');
  readonly refreshFailed = signal(false);
  readonly activeCount = computed(() => this.alerts().filter(alert => alert.enabled).length);
  readonly clearingIds = signal<number[]>([]);
  readonly clearingAll = signal(false);
  readonly confirmClearAll = signal(false);
  private noticesVersion = 0;

  instrumentId: number | null = null;
  direction: AlertDirection = 'ABOVE';
  threshold: number | null = null;
  editId: number | null = null;

  selectedInstrument(): MarketInstrumentResponse | null {
    return this.instruments().find(instrument => instrument.instrumentId === this.instrumentId) ?? null;
  }

  private readonly api = inject(PriceAlertsService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.reload();
    // Keep the alert state (Monitoring / Waiting for reset) and history current,
    // including transitions made by the backend while this tab is open.
    interval(5000).pipe(
      takeUntilDestroyed(this.destroyRef),
      exhaustMap(() => {
        if (this.busy() || this.loading() || this.clearingAll() || this.clearingIds().length) return of(null);
        const version = this.noticesVersion;
        return forkJoin({ alerts: this.api.list(), notices: this.api.notifications() })
          .pipe(map(data => ({data,version})), catchError(() => {
            if (version === this.noticesVersion) this.refreshFailed.set(true);
            return of(null);
          }));
      })
    ).subscribe(result => {
      // Discard a request that started before a successful clear operation.
      if (result !== null && result.version === this.noticesVersion) {
        this.refreshFailed.set(false);
        this.alerts.set(result.data.alerts);
        this.notices.set(result.data.notices);
      }
    });
  }

  reload(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    const version = ++this.noticesVersion;
    forkJoin({ alerts: this.api.list(), notices: this.api.notifications() }).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: result => {
        if (version !== this.noticesVersion) return;
        this.refreshFailed.set(false);
        this.alerts.set(result.alerts);
        this.notices.set(result.notices);
      },
      error: () => this.error.set('Could not load your saved price alerts. Please try again.')
    });
  }

  edit(alert: PriceAlert): void {
    this.editId = alert.id;
    this.instrumentId = alert.instrumentId;
    this.direction = alert.direction;
    this.threshold = alert.threshold;
    this.error.set('');
    this.info.set('Editing ' + alert.symbol + '. Saving will start a fresh monitoring cycle.');
  }

  reset(): void {
    this.editId = null;
    this.instrumentId = null;
    this.direction = 'ABOVE';
    this.threshold = null;
    this.info.set('');
  }

  save(): void {
    if (this.busy() || this.loading()) return;
    const id = Number(this.instrumentId);
    const amount = this.threshold;
    if (!Number.isInteger(id) || id <= 0 || amount === null ||
        !Number.isFinite(amount) || amount <= 0 ||
        !this.instruments().some(instrument => instrument.instrumentId === id)) {
      this.error.set('Select an instrument and enter a positive target price.');
      return;
    }

    const body: PriceAlertChange = {
      instrumentId: id, direction: this.direction, threshold: amount, enabled: true
    };
    this.noticesVersion++;
    this.busy.set(true);
    this.error.set('');
    this.info.set('');
    const request = this.editId === null ? this.api.create(body) : this.api.update(this.editId, body);
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.reset();
          this.info.set('Price alert saved. It will notify you again each time the price returns across your threshold.');
          this.reload();
        },
        error: () => this.error.set('Could not save this price alert. Check your details and retry.')
      });
  }

  toggle(alert: PriceAlert): void {
    if (this.busy() || this.loading()) return;
    this.noticesVersion++;
    this.busy.set(true);
    this.error.set('');
    this.api.enabled(alert.id, !alert.enabled).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false))
    ).subscribe({
      next: () => {
        this.info.set(alert.enabled ? 'Alert paused.' : 'Alert resumed. It will monitor for the next threshold crossing.');
        this.reload();
      },
      error: () => this.error.set('Could not update this price alert.')
    });
  }

  remove(alert: PriceAlert): void {
    if (this.busy() || this.loading()) return;
    this.noticesVersion++;
    this.busy.set(true);
    this.error.set('');
    this.api.delete(alert.id).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false))
    ).subscribe({
      next: () => {
        if (this.editId === alert.id) this.reset();
        this.info.set('Alert deleted.');
        this.reload();
      },
      error: () => this.error.set('Could not delete this price alert.')
    });
  }

  clearNotice(notice: PriceNotice): void {
    if (this.clearingAll() || this.clearingIds().includes(notice.id)) return;
    this.clearingIds.update(ids => [...ids, notice.id]);
    this.error.set('');
    this.api.clearNotice(notice.id).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.clearingIds.update(ids => ids.filter(id => id !== notice.id)))
    ).subscribe({
      next: () => {
        this.noticesVersion++;
        this.notices.update(items => items.filter(item => item.id !== notice.id));
        this.info.set('Notification cleared. Your price alert is still active.');
      },
      error: () => this.error.set('Could not clear that notification. Please try again.')
    });
  }

  clearAllNotices(): void {
    if (this.clearingAll() || this.clearingIds().length > 0 || this.notices().length === 0) return;
    this.confirmClearAll.set(false);
    this.clearingAll.set(true);
    this.error.set('');
    this.api.clearAllNotices().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.clearingAll.set(false))
    ).subscribe({
      next: () => {
        this.noticesVersion++;
        this.notices.set([]);
        this.info.set('All triggered notifications cleared. Your price alerts are unchanged.');
      },
      error: () => this.error.set('Could not clear your notifications. Please try again.')
    });
  }
}
