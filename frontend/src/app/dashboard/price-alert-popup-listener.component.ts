import { Component, DestroyRef, inject, input, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, exhaustMap, interval, map, of, startWith } from 'rxjs';
import { NotificationService } from '../core/services/notification';
import { PriceAlertsService, PriceNotice, NoticeClearedEvent } from '../core/services/price-alerts';

/**
 * Runs for the entire customer Dashboard route, NOT inside the hidden Watchlist tab.
 * Reuses Dashboard's existing notification popups and does not affect backend alerts.
 */
@Component({
  selector: 'app-price-alert-popup-listener',
  standalone: true,
  template: ''
})
export class PriceAlertPopupListenerComponent implements OnInit {
  readonly accountId = input<number | null>(null);

  private readonly api = inject(PriceAlertsService);
  private readonly notificationService = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly seenByAccount = new Map<number, Set<number>>();
  private readonly popupByNotice = new Map<number, string>();
  private readonly summaryPopups = new Set<string>();
  private requestEpoch = 0;

  ngOnInit(): void {
    // Successful Clear / Clear all actions also dismiss their current popups.
    this.api.noticeCleared$.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(event => this.onNoticeCleared(event));
    interval(5000).pipe(
      startWith(0),
      takeUntilDestroyed(this.destroyRef),
      exhaustMap(() => {
        const accountId = this.accountId();
        if (accountId === null) return of(null);
        const epoch = this.requestEpoch;
        return this.api.notifications().pipe(
          map(notices => ({ accountId, epoch, notices })),
          catchError(() => of(null)) // Temporary outages must not break subsequent checks.
        );
      })
    ).subscribe(result => {
      // Ignore responses from the previously signed-in account, if it changed.
      if (result === null || this.accountId() !== result.accountId || result.epoch !== this.requestEpoch) return;
      this.notifyNewAlerts(result.accountId, result.notices);
    });
  }

  private notifyNewAlerts(accountId: number, notices: PriceNotice[]): void {
    const seen = this.seenFor(accountId);
    const newUnread = notices.filter(notice => !notice.readAt && !seen.has(notice.id));
    if (newUnread.length === 0) return;

    // Record IDs before displaying popups so polling and tab changes cannot repeat them.
    for (const notice of newUnread) seen.add(notice.id);
    this.remember(accountId, seen);

    // A user returning after several offline alerts should not receive a wall of popups.
    for (const notice of newUnread.slice(0, 3)) {
      const verb = notice.direction === 'ABOVE' ? 'at or above' : 'at or below';
      const toastId = this.notificationService.show(
        `Price alert · ${notice.symbol}: ${this.format(notice.observedPrice)} ${notice.currency} ` +
        `(${verb} ${this.format(notice.threshold)} ${notice.currency}). View details in Watchlist → Price alerts.`,
        'info',
        5000
      );
      this.popupByNotice.set(notice.id, toastId);
    }
    if (newUnread.length > 3) {
      this.summaryPopups.add(this.notificationService.show(
        `${newUnread.length - 3} more price alerts triggered. See your Watchlist for details.`,
        'info',
        5000
      ));
    }
  }

  private onNoticeCleared(event: NoticeClearedEvent): void {
    const accountId = this.accountId();
    if (accountId === null) return;
    // Ignore any GET that was started before the completed DELETE.
    this.requestEpoch++;
    if (event.kind === 'one') {
      const seen = this.seenFor(accountId);
      seen.add(event.id);
      this.remember(accountId, seen);
      const toastId = this.popupByNotice.get(event.id);
      if (toastId) this.notificationService.dismiss(toastId);
      this.popupByNotice.delete(event.id);
    } else {
      for (const toastId of this.popupByNotice.values()) this.notificationService.dismiss(toastId);
      for (const toastId of this.summaryPopups) this.notificationService.dismiss(toastId);
      this.popupByNotice.clear();
      this.summaryPopups.clear();
    }
  }

  private format(value: number): string {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 8 }).format(value);
  }

  private seenFor(accountId: number): Set<number> {
    const cached = this.seenByAccount.get(accountId);
    if (cached) return cached;
    let ids: number[] = [];
    try {
      const saved = window.sessionStorage.getItem(this.storageKey(accountId));
      if (saved) {
        const parsed: unknown = JSON.parse(saved);
        if (Array.isArray(parsed)) {
          ids = parsed.filter((id): id is number => Number.isSafeInteger(id) && id > 0);
        }
      }
    } catch { /* Storage can be unavailable in privacy-restricted browsers. */ }
    const seen = new Set(ids);
    this.seenByAccount.set(accountId, seen);
    return seen;
  }

  private remember(accountId: number, seen: Set<number>): void {
    try {
      // Bound session storage size; backend serves only the 100 most recent notices.
      window.sessionStorage.setItem(
        this.storageKey(accountId), JSON.stringify([...seen].slice(-300))
      );
    } catch { /* The in-memory Set still prevents duplicate popups in this route. */ }
  }

  private storageKey(accountId: number): string {
    return `goatteen_price_alert_popups_${accountId}`;
  }
}
