import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PriceAlertsComponent } from './price-alerts.component';
import { PriceAlert, PriceNotice, PriceAlertsService } from '../core/services/price-alerts';

describe('PriceAlertsComponent notification clear actions', () => {
  const alert: PriceAlert = {
    id: 44, instrumentId: 1, symbol: 'AAPL', currency: 'USD', direction: 'ABOVE',
    threshold: 200, enabled: true, armed: false, generation: 1, triggeredAt: '2026-10-19T18:00:00'
  };
  const first: PriceNotice = {
    id: 101, alertId: 44, instrumentId: 1, symbol: 'AAPL', direction: 'ABOVE',
    threshold: 200, observedPrice: 210, currency: 'USD',
    quoteAt: '2026-10-19T18:00:00', createdAt: '2026-10-19T18:00:01', readAt: null
  };
  const second: PriceNotice = {
    ...first, id: 102, instrumentId: 2, symbol: 'TSLA', observedPrice: 250
  };
  const api = {
    list: vi.fn(), notifications: vi.fn(), clearNotice: vi.fn(), clearAllNotices: vi.fn()
  };

  beforeEach(async () => {
    api.list.mockReset().mockReturnValue(of([alert]));
    api.notifications.mockReset().mockReturnValue(of([first, second]));
    api.clearNotice.mockReset().mockReturnValue(of(void 0));
    api.clearAllNotices.mockReset().mockReturnValue(of(void 0));
    await TestBed.configureTestingModule({
      imports: [PriceAlertsComponent],
      providers: [{provide: PriceAlertsService, useValue: api}]
    }).compileComponents();
  });

  it('clears one notification without removing the configured price alert', () => {
    const fixture = TestBed.createComponent(PriceAlertsComponent);
    fixture.detectChanges();
    fixture.componentInstance.clearNotice(first);
    fixture.detectChanges();
    expect(api.clearNotice).toHaveBeenCalledWith(101);
    expect(fixture.componentInstance.notices().map(n => n.id)).toEqual([102]);
    expect(fixture.componentInstance.alerts()).toEqual([alert]);
    expect(fixture.nativeElement.textContent).toContain('Clear all notifications');
    fixture.destroy();
  });

  it('keeps alert form controls labelled and notification actions named', () => {
    const fixture = TestBed.createComponent(PriceAlertsComponent);
    fixture.componentRef.setInput('instruments', [{
      instrumentId: 1, symbol: 'AAPL', name: 'Apple', exchange: 'NASDAQ',
      instrumentClass: 'EQUITY', countryCode: 'US', currency: 'USD', tradable: true,
      latestQuote: null
    }]);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;

    expect(root.querySelector('label[for="alert-instrument"]')?.textContent).toContain('Instrument');
    expect(root.querySelector('label[for="alert-direction"]')?.textContent).toContain('Notify me when');
    expect(root.querySelector('label[for="alert-threshold"]')?.textContent).toContain('Target price');
    expect(root.querySelector<HTMLButtonElement>('.clear-all-button')?.getAttribute('aria-label'))
      .toContain('Clear all triggered price alert notifications');
    expect(root.querySelector<HTMLButtonElement>('.notification-history button')?.getAttribute('aria-label'))
      .toContain('Clear AAPL triggered price notification');
    fixture.destroy();
  });

  it('clears the full notification inbox, not the alert configurations', () => {
    const fixture = TestBed.createComponent(PriceAlertsComponent);
    fixture.detectChanges();
    const clearAll = fixture.nativeElement.querySelector('.clear-all-button') as HTMLButtonElement;
    clearAll.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Yes, clear all');
    fixture.nativeElement.querySelector('.clear-all-button').click();
    fixture.detectChanges();
    expect(api.clearAllNotices).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.notices()).toEqual([]);
    expect(fixture.componentInstance.alerts()).toEqual([alert]);
    expect(fixture.nativeElement.textContent).toContain('No saved notifications');
    fixture.destroy();
  });

  it('keeps notifications visible when the clear request fails', () => {
    api.clearNotice.mockReturnValue(throwError(() => new Error('offline')));
    const fixture = TestBed.createComponent(PriceAlertsComponent);
    fixture.detectChanges();
    fixture.componentInstance.clearNotice(first);
    fixture.detectChanges();
    expect(fixture.componentInstance.notices()).toHaveLength(2);
    expect(fixture.componentInstance.error()).toContain('Could not clear');
    fixture.destroy();
  });

  it('shows loading feedback instead of empty states before the initial alert requests finish', () => {
    const alertsPending = new Subject<PriceAlert[]>();
    const noticesPending = new Subject<PriceNotice[]>();
    api.list.mockReturnValue(alertsPending);
    api.notifications.mockReturnValue(noticesPending);

    const fixture = TestBed.createComponent(PriceAlertsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.loading()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Loading saved price alerts');
    expect(fixture.nativeElement.textContent).toContain('Loading triggered notifications');
    expect(fixture.nativeElement.textContent).not.toContain('No alerts yet');
    expect(fixture.nativeElement.textContent).not.toContain('No saved notifications');

    alertsPending.next([]);
    alertsPending.complete();
    noticesPending.next([]);
    noticesPending.complete();
    fixture.detectChanges();
    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No alerts yet');
    expect(fixture.nativeElement.textContent).toContain('No saved notifications');
    fixture.destroy();
  });

  it('does not show empty states when the initial alerts request fails', () => {
    api.list.mockReturnValue(throwError(() => new Error('offline')));
    const fixture = TestBed.createComponent(PriceAlertsComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toContain('Could not load');
    expect(fixture.nativeElement.textContent).not.toContain('No alerts yet');
    expect(fixture.nativeElement.textContent).not.toContain('No saved notifications');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Could not load');
    fixture.destroy();
  });

  it('disables bulk clear during an in-flight individual deletion', () => {
    const pending = new Subject<void>();
    api.clearNotice.mockReturnValue(pending);
    const fixture = TestBed.createComponent(PriceAlertsComponent);
    fixture.detectChanges();
    fixture.componentInstance.clearNotice(first);
    fixture.componentInstance.clearAllNotices();
    expect(api.clearAllNotices).not.toHaveBeenCalled();
    pending.next(); pending.complete();
    fixture.destroy();
  });

  it('keeps saved results visible and announces a failed background refresh until recovery', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(PriceAlertsComponent);
    try {
      fixture.detectChanges();
      api.list.mockReturnValue(throwError(() => new Error('offline')));
      vi.advanceTimersByTime(5000);
      fixture.detectChanges();
      expect(fixture.componentInstance.alerts()).toEqual([alert]);
      expect(fixture.componentInstance.notices()).toEqual([first, second]);
      expect(fixture.componentInstance.refreshFailed()).toBe(true);
      expect(fixture.nativeElement.textContent).toContain('Background refresh failed');
      api.list.mockReturnValue(of([alert]));
      vi.advanceTimersByTime(5000);
      fixture.detectChanges();
      expect(fixture.componentInstance.refreshFailed()).toBe(false);
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });

  it('ignores a stale polling failure after an explicit successful reload', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(PriceAlertsComponent);
    try {
      fixture.detectChanges();
      const oldPoll = new Subject<PriceAlert[]>();
      api.list.mockReturnValueOnce(oldPoll);
      vi.advanceTimersByTime(5000);
      fixture.componentInstance.reload();
      oldPoll.error(new Error('Old request failed after retry'));
      expect(fixture.componentInstance.refreshFailed()).toBe(false);
      expect(fixture.componentInstance.alerts()).toEqual([alert]);
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });
});
