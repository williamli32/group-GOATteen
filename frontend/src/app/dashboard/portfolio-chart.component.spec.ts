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
});
