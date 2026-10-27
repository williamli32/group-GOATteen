import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PositionResponse } from '../core/services/account';
import { FxQuote, FxRateService } from '../core/services/fx-rate';
import { MarketInstrumentResponse } from '../core/services/market-data';
import { buildHoldingsAllocation } from './holdings-allocation';
import { HoldingsAllocationComponent } from './holdings-allocation.component';

const now = Date.parse('2026-10-22T12:00:00Z');
const position = (id: number, quantity: number): PositionResponse => ({
  instrumentId: id,
  symbol: `SYM${id}`,
  instrumentName: `Instrument ${id}`,
  quantity,
});
const instrument = (id: number, currency: string, price: number): MarketInstrumentResponse => ({
  instrumentId: id,
  symbol: `SYM${id}`,
  name: `Instrument ${id}`,
  currency,
  instrumentClass: 'EQUITY',
  exchange: 'TEST',
  countryCode: 'GB',
  tradable: true,
  latestQuote: {
    quoteId: id,
    instrumentId: id,
    symbol: `SYM${id}`,
    bidPrice: price,
    askPrice: price,
    lastPrice: price,
    quotedAt: '2026-10-22T12:00:00',
  },
});
const conversion = (from: string, to: string, rate: number): FxQuote => ({
  from,
  to,
  rate,
  asOf: '2026-10-22',
  provider: 'TEST',
});

describe('Holdings allocation', () => {
  let fixture: ComponentFixture<HoldingsAllocationComponent> | undefined;
  afterEach(() => {
    fixture?.destroy();
    fixture = undefined;
    vi.useRealTimers();
  });

  async function mount(
    quote: ReturnType<typeof vi.fn>,
    positions: PositionResponse[],
    market: MarketInstrumentResponse[],
  ) {
    await TestBed.configureTestingModule({
      imports: [HoldingsAllocationComponent],
      providers: [{ provide: FxRateService, useValue: { quote } }],
    }).compileComponents();
    fixture = TestBed.createComponent(HoldingsAllocationComponent);
    fixture.componentRef.setInput('positions', positions);
    fixture.componentRef.setInput('instruments', market);
    fixture.componentRef.setInput('accountCurrency', 'GBP');
    fixture.componentRef.setInput('now', now);
    fixture.detectChanges();
    return fixture;
  }

  it('compares GBP, USD and INR holdings by converted market value, not quantity or raw notional', () => {
    const model = buildHoldingsAllocation(
      [position(1, 100), position(2, 5), position(3, 1000)],
      [instrument(1, 'USD', 10), instrument(2, 'GBP', 100), instrument(3, 'INR', 100)],
      'GBP',
      { USD: conversion('USD', 'GBP', 0.5), INR: conversion('INR', 'GBP', 0.01) },
      now,
    );
    expect(model.total).toBe(2000);
    expect(model.slices.map((slice) => [slice.instrumentId, slice.value, slice.share])).toEqual([
      [3, 1000, 50],
      [1, 500, 25],
      [2, 500, 25],
    ]);
    expect(model.slices.reduce((sum, slice) => sum + slice.share, 0)).toBeCloseTo(100);
    expect(model.missing).toEqual([]);
    expect(model.stale).toBe(false);
    expect(model.oldestQuote).toBe('2026-10-22T12:00:00.000Z');
  });

  it('handles empty and single holdings and excludes missing, invalid and wrong-direction FX data', () => {
    const market = [
      instrument(1, 'GBP', 0.004),
      instrument(2, 'USD', 10),
      { ...instrument(3, 'INR', 10), latestQuote: null },
      instrument(4, 'GBP', NaN),
    ];
    const rates = { USD: conversion('GBP', 'USD', 2) };
    const empty = buildHoldingsAllocation([position(1, 0)], market, 'GBP', rates, now);
    expect(empty.slices).toEqual([]);
    expect(empty.total).toBe(0);
    expect(empty.holdingCount).toBe(0);
    const single = buildHoldingsAllocation(
      [position(1, 1), position(2, 1), position(3, 1), position(4, 1)],
      market,
      'GBP',
      rates,
      now + 20_000,
    );
    expect(single.total).toBe(0.004);
    expect(single.slices[0].share).toBe(100);
    expect(single.slices[0].path.match(/A100,100/g)).toHaveLength(2);
    expect(single.slices[0].path).not.toMatch(/NaN|Infinity/);
    expect(single.missing).toEqual(['SYM2', 'SYM3', 'SYM4']);
    expect(single.stale).toBe(true);
  });

  it('waits for conversions before drawing and revalues price updates without refetching identical FX pairs', async () => {
    const pending = new Subject<FxQuote>();
    const quote = vi.fn(() => pending);
    const view = await mount(
      quote,
      [position(1, 2), position(2, 2)],
      [instrument(1, 'GBP', 10), instrument(2, 'USD', 20)],
    );
    expect(view.nativeElement.querySelector('svg')).toBeNull();
    expect(view.nativeElement.textContent).toContain('Updating prices');
    pending.next(conversion('USD', 'GBP', 0.5));
    pending.complete();
    view.detectChanges();
    expect(view.componentInstance.allocation().total).toBe(40);
    expect(view.nativeElement.querySelectorAll('svg path')).toHaveLength(2);
    view.componentRef.setInput('instruments', [instrument(1, 'GBP', 10), instrument(2, 'USD', 40)]);
    view.detectChanges();
    expect(view.componentInstance.allocation().total).toBe(60);
    expect(quote).toHaveBeenCalledTimes(1);
    expect(quote).toHaveBeenCalledWith('USD', 'GBP');
  });

  it('explains a partial allocation after an FX failure and recovers through the rendered retry action', async () => {
    let failed = true;
    const quote = vi.fn((from: string, to: string) =>
      from === 'USD' && failed
        ? throwError(() => ({ status: 503 }))
        : of(conversion(from, to, from === 'USD' ? 0.5 : 0.01)),
    );
    const view = await mount(
      quote,
      [position(1, 2), position(2, 2), position(3, 100)],
      [instrument(1, 'GBP', 10), instrument(2, 'USD', 20), instrument(3, 'INR', 100)],
    );
    expect(view.componentInstance.allocation().total).toBe(120);
    expect(view.nativeElement.textContent).toContain('Not valued: SYM2');
    expect(view.nativeElement.textContent).toContain('Percentages cover valued holdings only');
    failed = false;
    const retry = [...view.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.includes('Retry conversion'),
    ) as HTMLButtonElement;
    retry.click();
    view.detectChanges();
    expect(view.componentInstance.allocation().total).toBe(140);
    expect(view.componentInstance.allocation().missing).toEqual([]);
    expect(view.nativeElement.querySelector('.allocation-warning')).toBeNull();
  });

  it('renders themed hover cards with consistent currency and readable small allocations', async () => {
    const quote = vi.fn();
    const view = await mount(
      quote,
      [position(1, 100), position(2, 1), position(3, 1)],
      [
        instrument(1, 'GBP', 12.34567895),
        instrument(2, 'GBP', 0.0005),
        instrument(3, 'GBP', 0.0000001),
      ],
    );
    const paths = [...view.nativeElement.querySelectorAll('svg path')] as SVGPathElement[];
    const expected = [
      ['GBP 1,234.57', '100.0%'],
      ['GBP 0.0005', '<0.1%'],
      ['GBP <0.000001', '<0.1%'],
    ];
    paths.forEach((path, index) => {
      path.dispatchEvent(new MouseEvent('pointerenter', { clientX: 120, clientY: 120 }));
      view.detectChanges();
      const tooltip = view.nativeElement.querySelector('[role="tooltip"]') as HTMLElement;
      expect(tooltip.querySelector('.tooltip-heading strong')?.textContent).toBe(
        'SYM' + (index + 1),
      );
      expect(tooltip.querySelector('.tooltip-name')?.textContent).toContain(
        'Instrument ' + (index + 1),
      );
      expect([...tooltip.querySelectorAll('dd')].map((value) => value.textContent?.trim())).toEqual(
        expected[index],
      );
      expect(tooltip.textContent).not.toContain('&lt;');
    });
    expect(view.nativeElement.querySelector('svg title')).toBeNull();
    const values = [...view.nativeElement.querySelectorAll('.allocation-value small')].map(
      (element: HTMLElement) => element.textContent?.trim(),
    );
    expect(values).toEqual(['GBP 1,234.57', 'GBP 0.0005', 'GBP <0.000001']);
    view.nativeElement.querySelector('.pie-stage').dispatchEvent(new MouseEvent('pointerleave'));
    view.detectChanges();
    expect(view.nativeElement.querySelector('[role="tooltip"]')).toBeNull();
    expect(quote).not.toHaveBeenCalled();
  });

  it('bounds the tooltip, follows refreshed values, supports keyboard focus and dismisses stale selections', async () => {
    const view = await mount(
      vi.fn(),
      [position(1, 1), position(2, 1)],
      [instrument(1, 'GBP', 10), instrument(2, 'GBP', 20)],
    );
    const stage = view.nativeElement.querySelector('.pie-stage') as HTMLElement;
    const body = view.nativeElement.querySelector('.allocation-body') as HTMLElement;
    vi.spyOn(body, 'getBoundingClientRect').mockReturnValue({
      x: 100,
      y: 100,
      left: 100,
      top: 100,
      right: 340,
      bottom: 340,
      width: 240,
      height: 240,
      toJSON: () => ({}),
    });
    const paths = [...view.nativeElement.querySelectorAll('svg path')] as SVGPathElement[];
    paths[0].dispatchEvent(new MouseEvent('pointerenter', { clientX: 339, clientY: 339 }));
    view.detectChanges();
    const tooltip = view.nativeElement.querySelector('[role="tooltip"]') as HTMLElement;
    vi.spyOn(tooltip, 'getBoundingClientRect').mockReturnValue({
      x: 0,
      y: 0,
      left: 0,
      top: 0,
      right: 200,
      bottom: 130,
      width: 200,
      height: 130,
      toJSON: () => ({}),
    });
    paths[0].dispatchEvent(new MouseEvent('pointermove', { clientX: 339, clientY: 339 }));
    view.detectChanges();
    const point = view.componentInstance.tooltipPosition();
    expect(point.x).toBeGreaterThanOrEqual(8);
    expect(point.y).toBeGreaterThanOrEqual(8);
    expect(point.x + 200).toBeLessThanOrEqual(232);
    expect(point.y + 130).toBeLessThanOrEqual(232);
    view.componentRef.setInput('instruments', [instrument(1, 'GBP', 10), instrument(2, 'GBP', 40)]);
    view.detectChanges();
    expect(tooltip.querySelector('dd')?.textContent).toContain('GBP 40.00');
    const button = view.nativeElement.querySelector('.allocation-instrument') as HTMLButtonElement;
    button.focus();
    view.detectChanges();
    paths[1].dispatchEvent(new MouseEvent('pointerenter'));
    view.detectChanges();
    expect(tooltip.querySelector('.tooltip-heading strong')?.textContent).toBe('SYM1');
    stage.dispatchEvent(new MouseEvent('pointerleave'));
    view.detectChanges();
    expect(tooltip.querySelector('.tooltip-heading strong')?.textContent).toBe('SYM2');
    expect(button.getAttribute('aria-describedby')).toBe(tooltip.id);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    view.detectChanges();
    expect(view.nativeElement.querySelector('[role="tooltip"]')).toBeNull();
    button.blur();
    button.focus();
    view.detectChanges();
    expect(view.nativeElement.querySelector('[role="tooltip"]')).not.toBeNull();
    view.componentRef.setInput('positions', [position(1, 1)]);
    view.detectChanges();
    expect(view.nativeElement.querySelector('[role="tooltip"]')).toBeNull();
  });

  it('cancels obsolete account-currency conversions and stops FX polling when the chart is destroyed', async () => {
    vi.useFakeTimers();
    const old = new Subject<FxQuote>();
    const current = new Subject<FxQuote>();
    const quote = vi
      .fn()
      .mockReturnValueOnce(old)
      .mockReturnValueOnce(current)
      .mockReturnValue(of(conversion('USD', 'INR', 80)));
    const view = await mount(quote, [position(1, 2)], [instrument(1, 'USD', 20)]);
    expect(old.observed).toBe(true);
    view.componentRef.setInput('accountCurrency', 'INR');
    view.detectChanges();
    expect(old.observed).toBe(false);
    old.next(conversion('USD', 'GBP', 0.5));
    old.complete();
    current.next(conversion('USD', 'INR', 80));
    current.complete();
    view.detectChanges();
    expect(view.componentInstance.allocation().total).toBe(3200);
    vi.advanceTimersByTime(60_000);
    view.detectChanges();
    expect(quote).toHaveBeenCalledTimes(3);
    expect(quote).toHaveBeenLastCalledWith('USD', 'INR');
    view.destroy();
    vi.advanceTimersByTime(120_000);
    expect(quote).toHaveBeenCalledTimes(3);
  });
});
