import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { MarketInstrumentResponse } from '../core/services/market-data';
import { InstrumentPriceChartComponent } from './instrument-price-chart.component';
import { PRICE_WINDOW_MS } from './instrument-price-history';

const now = Date.UTC(2026, 9, 23, 12);
function instrument(price = 100, at = now, quoteId = 1, id = 1): MarketInstrumentResponse {
  return {
    instrumentId: id,
    symbol: id === 1 ? 'VOD.L' : 'AAPL',
    name: 'Chart test',
    instrumentClass: 'EQUITY',
    exchange: 'LSE',
    countryCode: 'GB',
    currency: 'GBP',
    tradable: true,
    latestQuote: {
      quoteId,
      instrumentId: id,
      symbol: id === 1 ? 'VOD.L' : 'AAPL',
      bidPrice: price,
      askPrice: price,
      lastPrice: price,
      quotedAt: new Date(at).toISOString(),
    },
  };
}

describe('Selected instrument price chart', () => {
  let fixture: ComponentFixture<InstrumentPriceChartComponent>;
  let component: InstrumentPriceChartComponent;
  beforeEach(() => {
    fixture = TestBed.createComponent(InstrumentPriceChartComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('instrument', instrument());
    fixture.componentRef.setInput('now', now);
    fixture.detectChanges();
  });
  afterEach(() => fixture.destroy());

  it('labels generated history separately and shows the real quote in its instrument currency', () => {
    expect(component.windowStart()).toBe(now - PRICE_WINDOW_MS);
    expect(component.previewPoints().length).toBeGreaterThan(50);
    expect(component.livePoints()).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('.live-price').textContent).toContain('GBP');
    expect(fixture.nativeElement.querySelector('.live-price').textContent).toContain('100.00');
    expect(fixture.nativeElement.querySelector('.source-note').textContent).toContain(
      'generated for illustration',
    );
    expect(fixture.nativeElement.querySelector('.live-change').textContent).toContain(
      'since live tracking began',
    );
  });

  it('extends the live line from incoming quotes and restores data when switching instruments', () => {
    const generated = component
      .history()!
      .points.filter((point) => point.source === 'illustrative');
    fixture.componentRef.setInput('now', now + 3000);
    fixture.componentRef.setInput('instrument', instrument(102, now + 3000, 2));
    fixture.detectChanges();
    expect(component.history()!.points.filter((point) => point.source === 'illustrative')).toEqual(
      generated,
    );
    expect(component.livePoints()).toHaveLength(2);
    expect(component.liveChange()).toBe(2);
    expect(component.livePath()).toContain('L');
    fixture.componentRef.setInput('instrument', instrument(500, now + 3000, 2, 2));
    fixture.detectChanges();
    expect(component.history()!.latest.price).toBe(500);
    fixture.componentRef.setInput('instrument', instrument(102, now + 3000, 2));
    fixture.detectChanges();
    expect(component.livePoints()).toHaveLength(2);
    expect(component.history()!.firstLive.price).toBe(100);
  });

  it('does not invent updates when the clock advances or requests fail and exposes a refresh action', () => {
    const before = component.history();
    const refresh = vi.fn();
    component.refreshPrices.subscribe(refresh);
    fixture.componentRef.setInput('now', now + 30_000);
    fixture.componentRef.setInput('refreshFailed', true);
    fixture.detectChanges();
    expect(component.history()).toBe(before);
    expect(component.live()).toBe(false);
    expect(fixture.nativeElement.querySelector('.live-state').textContent).toContain(
      'Updates paused',
    );
    expect(fixture.nativeElement.querySelector('.chart-warning').textContent).toContain(
      'Market refresh failed',
    );
    fixture.nativeElement.querySelector('.chart-warning button').click();
    expect(refresh).toHaveBeenCalledOnce();
  });

  it('exposes generated and live observations by keyboard and returns to the latest quote with Escape', () => {
    const plot = fixture.nativeElement.querySelector('.instrument-price-plot') as HTMLElement;
    plot.focus();
    for (const [key, source] of [
      ['Home', 'Illustrative price'],
      ['End', 'Live quote'],
      ['ArrowLeft', 'Illustrative price'],
    ]) {
      plot.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.price-inspection').textContent).toContain(
        source,
      );
    }
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(component.selectedPoint()).toBeNull();
    expect(fixture.nativeElement.querySelector('.price-inspection').textContent).toContain(
      'Latest quote',
    );
  });

  it('lets touch users inspect the plot without aiming at a small SVG marker', () => {
    const plot = fixture.nativeElement.querySelector('.instrument-price-plot') as HTMLElement;
    vi.spyOn(plot, 'getBoundingClientRect').mockReturnValue({ left: 0, width: 300 } as DOMRect);
    const touch = new MouseEvent('pointerdown', { clientX: 150, bubbles: true });
    Object.defineProperty(touch, 'pointerType', { value: 'touch' });
    plot.dispatchEvent(touch);
    plot.dispatchEvent(new Event('pointerleave'));
    fixture.detectChanges();
    expect(component.selectedPoint()?.source).toBe('illustrative');
    expect(fixture.nativeElement.querySelector('.price-inspection').textContent).toContain('UTC');
  });

  it('offers a live-detail scale with only real quotes and resets inspection when changing views', () => {
    fixture.componentRef.setInput('now', now + 3000);
    fixture.componentRef.setInput('instrument', instrument(101, now + 3000, 2));
    fixture.detectChanges();
    component.selectedTime.set(now - 12 * 3_600_000);
    const buttons = [
      ...fixture.nativeElement.querySelectorAll('.range-controls button'),
    ] as HTMLButtonElement[];
    buttons.find((button) => button.textContent?.trim() === 'Live detail')!.click();
    fixture.detectChanges();
    expect(component.view()).toBe('live');
    expect(component.selectedPoint()).toBeNull();
    expect(component.previewPoints()).toEqual([]);
    expect(component.points().every((point) => point.source === 'live')).toBe(true);
    expect(
      component.x(component.livePoints().at(-1)!) - component.x(component.livePoints()[0]),
    ).toBeGreaterThan(40);
    expect(component.timeTicks()).toHaveLength(3);
  });

  it('does not connect the live line across a period with no captured quotes', () => {
    for (const [elapsed, id] of [
      [3000, 2],
      [120_000, 3],
      [123_000, 4],
    ]) {
      fixture.componentRef.setInput('now', now + elapsed);
      fixture.componentRef.setInput('instrument', instrument(100 + id, now + elapsed, id));
      fixture.detectChanges();
    }
    expect(component.livePath().match(/M/g)).toHaveLength(2);
  });

  it('accepts a new quote when the dashboard clock catches up without needing another market response', () => {
    fixture.componentRef.setInput('instrument', instrument(103, now + 300, 2, 2));
    fixture.detectChanges();
    expect(component.history()).toBeNull();
    fixture.componentRef.setInput('now', now + 1000);
    fixture.detectChanges();
    expect(component.history()!.latest.price).toBe(103);
    expect(component.history()!.latest.timestamp).toBe(now + 300);
  });

  it('waits for a quote for an unquoted instrument and does not record hidden chart updates', () => {
    fixture.componentRef.setInput('instrument', {
      ...instrument(100, now, 1, 2),
      latestQuote: null,
    });
    fixture.detectChanges();
    expect(component.history()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Waiting for a current quote');
    fixture.componentRef.setInput('active', false);
    fixture.componentRef.setInput('instrument', instrument(500, now, 2, 2));
    fixture.detectChanges();
    expect(component.history()).toBeNull();
    fixture.componentRef.setInput('active', true);
    fixture.detectChanges();
    expect(component.history()!.latest.price).toBe(500);
  });
});
