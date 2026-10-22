import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { MarketInstrumentResponse } from '../core/services/market-data';
import { WatchlistService } from '../core/services/watchlist';
import { WatchlistComponent } from './watchlist.component';

describe('WatchlistComponent', () => {
  let fixture: ComponentFixture<WatchlistComponent>;
  let component: WatchlistComponent;
  const service = { getWatchlist: vi.fn(), add: vi.fn(), remove: vi.fn() };
  const first: MarketInstrumentResponse = {
    instrumentId: 1, symbol: 'RIO', name: 'Rio Tinto US', exchange: 'NYSE',
    instrumentClass: 'EQUITY', countryCode: 'US', currency: 'USD', tradable: true,
    latestQuote: { quoteId: 10, instrumentId: 1, symbol: 'RIO', bidPrice: 99,
      askPrice: 101, lastPrice: 100, quotedAt: '2026-10-19T10:00:00' }
  };
  const second: MarketInstrumentResponse = {
    ...first, instrumentId: 2, exchange: 'LSE', name: 'Rio Tinto UK', countryCode: 'GB',
    currency: 'GBP', latestQuote: null
  };
  const reference = {
    percent: 0, referencePrice: 100, tradingDay: '2026-10-19', referenceTime: '2026-10-19T09:00:00Z'
  };

  beforeEach(async () => {
    service.getWatchlist.mockReset().mockReturnValue(of([first]));
    service.add.mockReset().mockReturnValue(of(void 0));
    service.remove.mockReset().mockReturnValue(of(void 0));
    await TestBed.configureTestingModule({
      imports: [WatchlistComponent], providers: [{ provide: WatchlistService, useValue: service }]
    }).compileComponents();
    fixture = TestBed.createComponent(WatchlistComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('instruments', [first, second]);
    fixture.detectChanges();
  });

  it('loads saved instruments and sends the latest market quote to the order ticket', () => {
    const selected = vi.fn();
    component.instrumentSelected.subscribe(selected);
    const updated = { ...first, dayChange: { ...reference, percent: 10 },
      latestQuote: { ...first.latestQuote!, lastPrice: 110, askPrice: 111 } };
    fixture.componentRef.setInput('instruments', [updated, second]);
    fixture.detectChanges();
    expect(service.getWatchlist).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('.quote').textContent).toContain('110.00 USD');
    expect(fixture.nativeElement.querySelector('app-daily-price-change').textContent.trim()).toBe('+10.00%');
    expect(fixture.nativeElement.querySelector('app-daily-price-change span').title).toContain('100 USD');
    fixture.nativeElement.querySelector('.instrument-button').click();
    expect(selected).toHaveBeenCalledWith(updated);
    fixture.componentRef.setInput('instruments', [{ ...updated, dayChange: { ...reference, percent: -2.5 },
      latestQuote: { ...updated.latestQuote, lastPrice: 97.5 } }, second]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-daily-price-change').textContent.trim()).toBe('-2.50%');
    // Updating quotes does not refetch or rewrite saved membership.
    expect(service.add).not.toHaveBeenCalled();
  });

  it('keeps watchlist controls labelled and instrument actions named', () => {
    const root: HTMLElement = fixture.nativeElement;
    const instrumentLabel = root.querySelector<HTMLLabelElement>('label[for="watchlist-instrument"]');
    const instrumentSelect = root.querySelector<HTMLSelectElement>('#watchlist-instrument');
    const instrumentButton = root.querySelector<HTMLButtonElement>('.instrument-button');
    const removeButton = root.querySelector<HTMLButtonElement>('.remove-button');

    expect(instrumentLabel?.textContent).toContain('Add an instrument');
    expect(instrumentSelect).not.toBeNull();
    expect(instrumentButton?.getAttribute('aria-label')).toContain('Select RIO on NYSE');
    expect(removeButton?.getAttribute('aria-label')).toContain('Remove RIO on NYSE');
  });

  it('does not replace a newer saved quote with an older market response', () => {
    const selected = vi.fn();
    component.instrumentSelected.subscribe(selected);
    fixture.componentRef.setInput('instruments', [{ ...first, dayChange: { ...reference, percent: 0 } }, second]);
    service.getWatchlist.mockReturnValue(of([{ ...first, latestQuote: {
      ...first.latestQuote!, quoteId: 11, lastPrice: 110, askPrice: 111, quotedAt: '2026-10-19T10:00:03'
    } }]));
    component.reload();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.quote').textContent).toContain('110.00 USD');
    expect(fixture.nativeElement.querySelector('app-daily-price-change').textContent.trim()).toBe('—');
    fixture.nativeElement.querySelector('.instrument-button').click();
    expect(selected.mock.calls[0][0].latestQuote.askPrice).toBe(111);
    // Once the market catches up, its price and percentage appear together.
    fixture.componentRef.setInput('instruments', [{ ...first, dayChange: { ...reference, percent: 12 },
      latestQuote: { ...first.latestQuote!, quoteId: 12, lastPrice: 112, quotedAt: '2026-10-19T10:00:06' } }, second]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.quote').textContent).toContain('112.00 USD');
    expect(fixture.nativeElement.querySelector('app-daily-price-change').textContent.trim()).toBe('+12.00%');
  });

  it('saves the same symbol on another exchange as a separate instrument', () => {
    component.addId = '2';
    component.addSelected();
    fixture.detectChanges();
    expect(service.add).toHaveBeenCalledWith(2);
    expect(fixture.nativeElement.querySelectorAll('.watchlist-items li').length).toBe(2);
    expect(component.availableInstruments()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('LSE');
    expect(fixture.nativeElement.textContent).toContain('USD');
    expect(fixture.nativeElement.textContent).toContain('GBP');
  });

  it('waits for confirmation and blocks repeated clicks during a save', () => {
    const pending = new Subject<void>();
    service.add.mockReturnValue(pending);
    component.addId = '2';
    component.addSelected();
    component.addSelected();
    component.reload();
    fixture.detectChanges();
    expect(service.add).toHaveBeenCalledTimes(1);
    expect(service.getWatchlist).toHaveBeenCalledTimes(1);
    expect(component.savedInstruments()).toEqual([first]);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    pending.next(); pending.complete();
    fixture.detectChanges();
    expect(component.savedInstruments()).toHaveLength(2);
    expect(component.pendingId()).toBeNull();
  });

  it('retains the selection on a failed addition and reconciles an uncertain save on refresh', () => {
    service.add.mockReturnValue(throwError(() => ({ status: 0 })));
    component.addId = '2';
    component.addSelected();
    expect(component.savedInstruments()).toEqual([first]);
    expect(component.addId).toBe('2');
    expect(component.error()).toContain('could not confirm');
    // The server might have committed the PUT before the connection failed.
    service.getWatchlist.mockReturnValue(of([first, second]));
    component.reload();
    component.addSelected();
    expect(component.savedInstruments()).toHaveLength(2);
    expect(service.add).toHaveBeenCalledTimes(1);
  });

  it('keeps an entry after a failed removal and removes it only after confirmation', () => {
    service.remove.mockReturnValueOnce(throwError(() => ({ status: 503 }))).mockReturnValue(of(void 0));
    component.remove(first);
    expect(component.savedInstruments()).toEqual([first]);
    expect(component.error()).toContain('could not confirm');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.remove-button').click();
    fixture.detectChanges();
    expect(service.remove).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Your watchlist is empty');
    expect(component.success()).toContain('removed');
  });

  it('does not claim the watchlist is empty when its initial load fails', () => {
    fixture.destroy();
    service.getWatchlist.mockReturnValue(throwError(() => ({ status: 500 })));
    fixture = TestBed.createComponent(WatchlistComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('instruments', [first, second]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('could not be loaded');
    expect(fixture.nativeElement.textContent).not.toContain('Your watchlist is empty');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    service.getWatchlist.mockReturnValue(of([first]));
    fixture.nativeElement.querySelector('.refresh-button').click();
    fixture.detectChanges();
    expect(component.savedInstruments()).toEqual([first]);
    expect(component.error()).toBe('');
  });

  it('keeps unavailable saved instruments visible and removable without enabling trading', () => {
    const selected = vi.fn();
    component.instrumentSelected.subscribe(selected);
    fixture.componentRef.setInput('instruments', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Rio Tinto US');
    expect(fixture.nativeElement.querySelector('.instrument-button').disabled).toBe(true);
    component.select(first);
    expect(selected).not.toHaveBeenCalled();
    fixture.nativeElement.querySelector('.remove-button').click();
    expect(service.remove).toHaveBeenCalledWith(1);
  });

  it('does not trade a missing quote or change instruments during order submission', () => {
    service.getWatchlist.mockReturnValue(of([second]));
    component.reload();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Price unavailable');
    expect(fixture.nativeElement.querySelector('app-daily-price-change').textContent.trim()).toBe('—');
    expect(fixture.nativeElement.querySelector('.instrument-button').disabled).toBe(true);
    service.getWatchlist.mockReturnValue(of([first]));
    component.reload();
    fixture.componentRef.setInput('selectionDisabled', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.instrument-button').disabled).toBe(true);
  });

  it('labels an unavailable market feed while retaining the last saved quote time', () => {
    fixture.componentRef.setInput('marketError', 'Unable to load market data');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Market updates are unavailable');
    expect(fixture.nativeElement.querySelector('.quote small').textContent).toContain('Quote:');
    expect(fixture.nativeElement.querySelector('.instrument-button').disabled).toBe(true);
  });
  it('keeps the watchlist and alert editor mounted while changing views', () => {
    component.addId = '2';
    const editor = fixture.nativeElement.querySelector('app-price-alerts');
    const controls = [...fixture.nativeElement.querySelectorAll('.watchlist-views button')] as HTMLButtonElement[];
    expect(controls).toHaveLength(2);
    controls[1].click();
    fixture.detectChanges();
    expect(component.view()).toBe('alerts');
    expect(fixture.nativeElement.querySelector('#saved-instruments-view').hidden).toBe(true);
    expect(fixture.nativeElement.querySelector('#price-alerts-view').hidden).toBe(false);
    controls[0].click();
    fixture.detectChanges();
    expect(component.addId).toBe('2');
    expect(component.savedInstruments()).toEqual([first]);
    expect(fixture.nativeElement.querySelector('app-price-alerts')).toBe(editor);
    expect(service.getWatchlist).toHaveBeenCalledTimes(1);
  });

});
