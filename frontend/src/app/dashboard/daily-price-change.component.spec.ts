import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DailyPriceChangeComponent } from './daily-price-change.component';
import { DailyPriceChange } from '../core/services/market-data';

describe('DailyPriceChangeComponent', () => {
  let fixture: ComponentFixture<DailyPriceChangeComponent>;
  const reference: DailyPriceChange = {
    percent: 0, referencePrice: 100, tradingDay: '2026-10-19', referenceTime: '2026-10-19T09:00:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DailyPriceChangeComponent] }).compileComponents();
    fixture = TestBed.createComponent(DailyPriceChangeComponent);
    fixture.componentRef.setInput('currency', 'USD');
  });

  it.each([
    [1.25, '+1.25%', 'positive'],
    [-2.5, '-2.50%', 'negative'],
    [0, '0.00%', '']
  ])('formats the sign and direction of %s', (percent, text, direction) => {
    fixture.componentRef.setInput('change', { ...reference, percent });
    fixture.detectChanges();
    const cell = fixture.nativeElement.querySelector('span');
    expect(cell.textContent.trim()).toBe(text);
    if (direction) expect(cell.classList.contains(direction)).toBe(true);
    else expect(cell.className).toBe('');
  });

  it('shows an unavailable marker rather than an invented zero', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('—');
    fixture.componentRef.setInput('change', { ...reference, percent: Number.NaN });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('—');
  });

  it('explains the UTC reference date, time, price and instrument currency', () => {
    fixture.componentRef.setInput('change', reference);
    fixture.componentRef.setInput('currency', 'INR');
    fixture.detectChanges();
    const title = fixture.nativeElement.querySelector('span').title;
    expect(title).toContain('2026-10-19');
    expect(title).toContain('09:00:00 UTC');
    expect(title).toContain('100 INR');
  });
});
