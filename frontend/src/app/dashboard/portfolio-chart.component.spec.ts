import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PortfolioChartComponent, PortfolioValuePoint } from './portfolio-chart.component';
import { vi } from 'vitest';

describe('Portfolio chart access and data states', () => {
  let fixture: ComponentFixture<PortfolioChartComponent>;
  let component: PortfolioChartComponent;
  const point = (day: number, value: number | null): PortfolioValuePoint => ({
    timestamp: Date.UTC(2026, 9, day, 12),
    value,
    unavailable: value === null ? ['AAPL'] : [],
  });
  beforeEach(() => {
    fixture = TestBed.createComponent(PortfolioChartComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('currency', 'GBP');
    fixture.componentRef.setInput('value', 2080);
    fixture.componentRef.setInput('points', [point(20, 2000), point(21, 2050), point(22, 2080)]);
    fixture.detectChanges();
  });

  it('exposes each date and value by keyboard, including Home, End and Escape', () => {
    const chart = fixture.nativeElement.querySelector('.portfolio-value-chart') as HTMLElement;
    chart.focus();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.point-detail').textContent).toContain(
      '20 Oct 2026',
    );
    for (const [key, expected] of [
      ['End', '22 Oct 2026'],
      ['ArrowLeft', '21 Oct 2026'],
      ['Home', '20 Oct 2026'],
    ]) {
      chart.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.point-detail').textContent).toContain(expected);
    }
    chart.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(component.selectedPoint()).toBeNull();
  });

  it('lets a touch user inspect a day without relying on hovering a tiny SVG circle', () => {
    const chart = fixture.nativeElement.querySelector('.portfolio-value-chart') as HTMLElement;
    vi.spyOn(chart, 'getBoundingClientRect').mockReturnValue({ left: 0, width: 300 } as DOMRect);
    const event = new MouseEvent('pointerdown', { clientX: 300, bubbles: true });
    Object.defineProperty(event, 'pointerType', { value: 'touch' });
    chart.dispatchEvent(event);
    chart.dispatchEvent(new Event('pointerleave'));
    fixture.detectChanges();
    expect(component.selectedPoint()?.value).toBe(2080);
    expect(fixture.nativeElement.querySelector('.point-detail').textContent).toContain(
      '22 Oct 2026',
    );
  });

  it('breaks lines across missing data and shows unavailable instruments instead of zero', () => {
    fixture.componentRef.setInput('points', [
      point(19, 2000),
      point(20, null),
      point(21, 2050),
      point(22, 2080),
    ]);
    fixture.detectChanges();
    expect(component.segments()).toHaveLength(1);
    component.focusChart();
    component.keydown(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.point-detail').textContent).toContain(
      'Unavailable',
    );
    expect(fixture.nativeElement.querySelector('.point-detail').textContent).toContain('AAPL');
    expect(component.x(0)).toBe(0);
    expect(component.x(3)).toBe(800);
  });

  it('does not draw a trend between sparse nonadjacent dates and preserves real zero values', () => {
    fixture.componentRef.setInput('points', [point(19, 0), point(22, 2080)]);
    fixture.detectChanges();
    expect(component.valuedPoints()).toHaveLength(2);
    expect(component.segments()).toEqual([]);
    expect(component.money(0)).toContain('0.00');
    fixture.componentRef.setInput('points', [point(22, 0)]);
    fixture.detectChanges();
    expect(component.x(0)).toBe(400);
    expect(fixture.nativeElement.textContent).toContain('One complete observation');
  });

  it('keeps previous results labelled correctly and exposes a retry when another range fails', () => {
    fixture.componentRef.setInput('requestedRange', 30);
    fixture.componentRef.setInput('failed', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Still showing 5D',
    );
    expect(fixture.nativeElement.querySelector('.portfolio-range-caption').textContent).toContain(
      '5D',
    );
    const retry = vi.fn();
    component.retryHistory.subscribe(retry);
    (fixture.nativeElement.querySelector('.history-error button') as HTMLButtonElement).click();
    expect(retry).toHaveBeenCalledOnce();
  });

  it('makes a long history reachable in an accessible table with functional paging and page-size controls', () => {
    fixture.componentRef.setInput(
      'points',
      Array.from({ length: 51 }, (_, i) => ({
        timestamp: Date.UTC(2026, 7, 1 + i, 12),
        value: i + 1,
      })),
    );
    fixture.detectChanges();
    expect(component.tableRows()).toHaveLength(10);
    component.changePage(6);
    expect(component.tableRows()).toHaveLength(1);
    component.changePageSize(25);
    fixture.detectChanges();
    expect(component.page()).toBe(1);
    expect(component.tableRows()).toHaveLength(25);
    expect(fixture.nativeElement.querySelector('table').getAttribute('aria-label')).toBe(
      'Daily portfolio values',
    );
  });

  it('distinguishes loading, empty and completely unavailable histories', () => {
    fixture.componentRef.setInput('points', []);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading 5D history');
    expect(fixture.nativeElement.textContent).not.toContain('No account history');
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No account history');
    fixture.componentRef.setInput('points', [point(22, null)]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No complete valuations');
  });

  it('defaults to live observations when enabled and keeps daily history independently selectable', () => {
    const now = Date.UTC(2026, 9, 27, 12);
    fixture.componentRef.setInput('liveEnabled', true);
    fixture.componentRef.setInput('now', now);
    fixture.componentRef.setInput('livePoints', [
      { timestamp: now - 3000, value: 2080 },
      { timestamp: now, value: 2080.2 },
    ]);
    fixture.detectChanges();
    expect(component.isLive()).toBe(true);
    expect(component.maximum() - component.minimum()).toBeLessThan(1);
    expect(component.y(2080) - component.y(2080.2)).toBeGreaterThan(100);
    expect(component.tickLabel(now)).toBe('12:00:00');
    component.setView('daily', 5);
    fixture.detectChanges();
    expect(component.displayedPoints()).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('daily account values');
    component.setView('live');
    fixture.detectChanges();
    expect(component.displayedPoints()).toHaveLength(2);
  });

  it('keeps an inspected live timestamp selected as new samples arrive', () => {
    const now = Date.UTC(2026, 9, 27, 12);
    const points = [
      { timestamp: now - 3000, value: 2080 },
      { timestamp: now, value: 2081 },
    ];
    fixture.componentRef.setInput('liveEnabled', true);
    fixture.componentRef.setInput('now', now);
    fixture.componentRef.setInput('livePoints', points);
    fixture.detectChanges();
    component.focusChart();
    component.keydown(new KeyboardEvent('keydown', { key: 'Home' }));
    fixture.componentRef.setInput('now', now + 3000);
    fixture.componentRef.setInput('livePoints', [
      ...points,
      { timestamp: now + 3000, value: 2082 },
    ]);
    fixture.detectChanges();
    expect(component.selectedPoint()?.timestamp).toBe(now - 3000);
  });

  it('marks live gaps as paused and never connects through a missing valuation', () => {
    const now = Date.UTC(2026, 9, 27, 12);
    fixture.componentRef.setInput('liveEnabled', true);
    fixture.componentRef.setInput('now', now);
    fixture.componentRef.setInput('livePoints', [
      { timestamp: now - 6000, value: 2080 },
      { timestamp: now - 3000, value: null },
      { timestamp: now, value: 2082, breakBefore: true },
    ]);
    fixture.detectChanges();
    expect(component.segments()).toHaveLength(0);
    fixture.componentRef.setInput('now', now + 11000);
    fixture.detectChanges();
    expect(component.liveStatus()).toBe('Updates paused');
  });

  it('shows the nearest portfolio value, UTC time and observed change beside the pointer', async () => {
    const chart = fixture.nativeElement.querySelector('.portfolio-value-chart') as HTMLElement;
    vi.spyOn(chart, 'getBoundingClientRect').mockReturnValue({ left: 0, width: 300 } as DOMRect);
    chart.dispatchEvent(
      new MouseEvent('pointermove', { clientX: 299, clientY: 200, bubbles: true }),
    );
    fixture.detectChanges();
    await fixture.whenStable();
    const tooltip = fixture.nativeElement.querySelector('[role="tooltip"]') as HTMLElement;
    expect(tooltip.textContent).toContain('£2,080.00');
    expect(tooltip.textContent).toContain('22 Oct 2026');
    expect(tooltip.textContent).toContain('UTC');
    expect(tooltip.textContent).toContain('+£30.00');
    expect(tooltip.textContent).toContain('+1.46%');
    expect(tooltip.textContent).toContain('vs previous observation');
    expect(chart.getAttribute('aria-describedby')).toBe(tooltip.id);
  });

  it('flips the pointer tooltip near viewport edges and keeps it within the screen', async () => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      return (
        this.classList.contains('portfolio-tooltip')
          ? { left: 0, top: 0, width: 260, height: 160 }
          : { left: 0, top: 0, width: window.innerWidth, height: 220 }
      ) as DOMRect;
    });
    const chart = fixture.nativeElement.querySelector('.portfolio-value-chart') as HTMLElement;
    const x = window.innerWidth - 2,
      y = window.innerHeight - 2;
    chart.dispatchEvent(new MouseEvent('pointermove', { clientX: x, clientY: y, bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    const tooltip = fixture.nativeElement.querySelector('[role="tooltip"]') as HTMLElement;
    expect(Number.parseFloat(tooltip.style.left)).toBeLessThan(x);
    expect(Number.parseFloat(tooltip.style.top)).toBeLessThan(y);
    expect(Number.parseFloat(tooltip.style.left) + 260).toBeLessThanOrEqual(window.innerWidth - 12);
    expect(Number.parseFloat(tooltip.style.top) + 160).toBeLessThanOrEqual(window.innerHeight - 12);
    vi.restoreAllMocks();
  });

  it('dismisses pointer details on leave, scroll, resize and Escape while retaining keyboard inspection', () => {
    const chart = fixture.nativeElement.querySelector('.portfolio-value-chart') as HTMLElement;
    vi.spyOn(chart, 'getBoundingClientRect').mockReturnValue({ left: 0, width: 300 } as DOMRect);
    component.focusChart();
    const hover = () => {
      chart.dispatchEvent(
        new MouseEvent('pointermove', { clientX: 299, clientY: 120, bubbles: true }),
      );
      fixture.detectChanges();
    };
    hover();
    component.leave();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="tooltip"]')).toBeNull();
    expect(component.selectedPoint()).not.toBeNull();
    hover();
    window.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();
    expect(component.pointerAnchor()).toBeNull();
    hover();
    window.dispatchEvent(new Event('resize'));
    fixture.detectChanges();
    expect(component.pointerAnchor()).toBeNull();
    hover();
    component.keydown(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="tooltip"]')).toBeNull();
    component.keydown(new KeyboardEvent('keydown', { key: 'End' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.point-detail').textContent).toContain('£2,080.00');
  });

  it('does not calculate a change across missing observations or a line break', () => {
    fixture.componentRef.setInput('points', [point(20, 2000), point(21, null), point(22, 2080)]);
    fixture.detectChanges();
    component.selectedIndex.set(2);
    expect(component.selectedChange()).toBeNull();
    fixture.componentRef.setInput('points', [
      point(20, 2000),
      { ...point(21, 2050), breakBefore: true },
    ]);
    fixture.detectChanges();
    component.selectedIndex.set(1);
    expect(component.selectedChange()).toBeNull();
  });

  it('keeps hover details visible when the latest live sample is replaced during a refresh', () => {
    const now = Date.UTC(2026, 9, 27, 12);
    fixture.componentRef.setInput('liveEnabled', true);
    fixture.componentRef.setInput('now', now);
    fixture.componentRef.setInput('livePoints', [{ timestamp: now, value: 2080 }]);
    fixture.detectChanges();
    const chart = fixture.nativeElement.querySelector('.portfolio-value-chart') as HTMLElement;
    vi.spyOn(chart, 'getBoundingClientRect').mockReturnValue({ left: 0, width: 300 } as DOMRect);
    chart.dispatchEvent(
      new MouseEvent('pointermove', { clientX: 299, clientY: 120, bubbles: true }),
    );
    fixture.detectChanges();
    fixture.componentRef.setInput('now', now + 1000);
    fixture.componentRef.setInput('livePoints', [{ timestamp: now + 1000, value: 2081 }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="tooltip"]').textContent).toContain(
      '£2,081.00',
    );
  });
});
