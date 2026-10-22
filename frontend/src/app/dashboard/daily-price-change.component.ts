import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { DailyPriceChange } from '../core/services/market-data';

@Component({
  selector: 'app-daily-price-change',
  standalone: true,
  imports: [DecimalPipe],
  template: '<span [class.positive]="(validChange()?.percent ?? 0) > 0"'
    + ' [class.negative]="(validChange()?.percent ?? 0) < 0" [title]="description()">'
    + '@if (validChange(); as change) {'
    + '{{ change.percent > 0 ? "+" : "" }}{{ change.percent | number:"1.2-2" }}%'
    + '} @else { — }</span>',
  styles: [
    ':host { display: inline-block; white-space: nowrap; font-variant-numeric: tabular-nums; }'
    + 'span { color: var(--muted); font-weight: 600; }'
    + '.positive { color: var(--positive); } .negative { color: var(--negative); }'
  ]
})
export class DailyPriceChangeComponent {
  readonly change = input<DailyPriceChange | null>(null);
  readonly currency = input('');
  readonly validChange = computed(() => {
    const value = this.change();
    return value && Number.isFinite(value.percent) && value.referencePrice > 0 ? value : null;
  });
  readonly description = computed(() => {
    const value = this.validChange();
    if (!value) return 'Daily change is unavailable until a reference price and current quote are recorded today (UTC).';
    const price = new Intl.NumberFormat('en-GB', { maximumFractionDigits: 6 }).format(value.referencePrice);
    const time = new Date(value.referenceTime).toLocaleTimeString('en-GB', { timeZone: 'UTC' });
    return 'Change since the first recorded price on ' + value.tradingDay + ' (UTC): '
      + price + ' ' + this.currency() + ', recorded at ' + time + ' UTC.';
  });
}
