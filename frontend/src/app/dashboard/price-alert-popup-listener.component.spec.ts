import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { of, throwError, Subject } from 'rxjs';
import { PriceAlertPopupListenerComponent } from './price-alert-popup-listener.component';
import { PriceAlertsService, PriceNotice, NoticeClearedEvent } from '../core/services/price-alerts';
import { NotificationService } from '../core/services/notification';

describe('PriceAlertPopupListenerComponent', () => {
  let fixture: ComponentFixture<PriceAlertPopupListenerComponent>;
  let notices: PriceNotice[];
  const cleared = new Subject<NoticeClearedEvent>();
  const api = { notifications: vi.fn(), noticeCleared$: cleared.asObservable() };
  const toast = { show: vi.fn(), dismiss: vi.fn() };
  const key = 'goatteen_price_alert_popups_42';
  const event: PriceNotice = {
    id: 9001, alertId: 44, instrumentId: 1, symbol: 'AAPL', direction: 'ABOVE',
    threshold: 200, observedPrice: 205, currency: 'USD', quoteAt: '2026-10-19T18:30:00',
    createdAt: '2026-10-19T18:30:01', readAt: null
  };

  beforeEach(async () => {
    vi.useFakeTimers();
    sessionStorage.removeItem(key);
    notices = [event];
    api.notifications.mockReset().mockImplementation(() => of(notices));
    toast.show.mockReset().mockReturnValue('toast-for-alert');
    toast.dismiss.mockReset();
    await TestBed.configureTestingModule({
      imports: [PriceAlertPopupListenerComponent],
      providers: [
        { provide: PriceAlertsService, useValue: api },
        { provide: NotificationService, useValue: toast }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(PriceAlertPopupListenerComponent);
    fixture.componentRef.setInput('accountId', 42);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
    sessionStorage.removeItem(key);
  });

  it('shows a toast once for an unread triggered alert and never repeats it on polls', () => {
    expect(toast.show).toHaveBeenCalledTimes(1);
    expect(toast.show.mock.calls[0][0]).toContain('AAPL');
    expect(toast.show.mock.calls[0][0]).toContain('205 USD');
    expect(toast.show.mock.calls[0][2]).toBe(5000);
    vi.advanceTimersByTime(15000);
    expect(toast.show).toHaveBeenCalledTimes(1);
  });

  it('does not popup a read notification', () => {
    // Re-initialize with an already-read notification and a clean seen history.
    fixture.destroy();
    notices = [{ ...event, readAt: '2026-10-19T18:40:00' }];
    toast.show.mockReset().mockReturnValue('toast-for-alert');
    toast.dismiss.mockReset();
    sessionStorage.removeItem(key);
    fixture = TestBed.createComponent(PriceAlertPopupListenerComponent);
    fixture.componentRef.setInput('accountId', 42);
    fixture.detectChanges();
    expect(toast.show).not.toHaveBeenCalled();
  });

  it('remembers popup IDs within the browser session across component remounts', () => {
    fixture.destroy();
    fixture = TestBed.createComponent(PriceAlertPopupListenerComponent);
    fixture.componentRef.setInput('accountId', 42);
    fixture.detectChanges();
    expect(toast.show).toHaveBeenCalledTimes(1);
  });

  it('dismisses a cleared notification and does not show it again', () => {
    cleared.next({ kind: 'one', id: event.id });
    expect(toast.dismiss).toHaveBeenCalledWith('toast-for-alert');
    vi.advanceTimersByTime(5000);
    expect(toast.show).toHaveBeenCalledTimes(1);
  });

  it('dismisses price popups when all triggered notifications are cleared', () => {
    notices = [];
    cleared.next({ kind: 'all' });
    expect(toast.dismiss).toHaveBeenCalledWith('toast-for-alert');
    vi.advanceTimersByTime(5000);
    expect(toast.show).toHaveBeenCalledTimes(1);
  });

  it('ignores a stale pending GET response following a clear-all action', () => {
    fixture.destroy();
    sessionStorage.removeItem(key);
    toast.show.mockReset();
    const pending = new Subject<PriceNotice[]>();
    api.notifications.mockReturnValueOnce(pending).mockReturnValue(of([]));
    fixture = TestBed.createComponent(PriceAlertPopupListenerComponent);
    fixture.componentRef.setInput('accountId', 42);
    fixture.detectChanges();
    cleared.next({kind: 'all'});
    pending.next([event]);
    pending.complete();
    expect(toast.show).not.toHaveBeenCalled();
    vi.advanceTimersByTime(5000);
    expect(toast.show).not.toHaveBeenCalled();
  });

  it('recovers from a temporary network failure', () => {
    fixture.destroy();
    sessionStorage.removeItem(key);
    toast.show.mockReset().mockReturnValue('toast-for-alert');
    toast.dismiss.mockReset();
    api.notifications.mockReset()
      .mockReturnValueOnce(throwError(() => new Error('temporary outage')))
      .mockReturnValue(of(notices));
    fixture = TestBed.createComponent(PriceAlertPopupListenerComponent);
    fixture.componentRef.setInput('accountId', 42);
    fixture.detectChanges();
    expect(toast.show).not.toHaveBeenCalled();
    vi.advanceTimersByTime(5000);
    expect(toast.show).toHaveBeenCalledTimes(1);
  });
});
