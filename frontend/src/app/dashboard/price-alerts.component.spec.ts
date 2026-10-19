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
});
