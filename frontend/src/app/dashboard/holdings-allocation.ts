import { PositionResponse } from '../core/services/account';
import { FxQuote } from '../core/services/fx-rate';
import { MarketInstrumentResponse } from '../core/services/market-data';

export interface AllocationSlice extends PositionResponse {
  value: number;
  share: number;
  color: string;
  path: string;
}

export interface HoldingsAllocation {
  slices: AllocationSlice[];
  total: number;
  holdingCount: number;
  missing: string[];
  stale: boolean;
  oldestQuote: string | null;
}

function timestamp(value: string): number {
  // Market timestamps without an offset are recorded in UTC by the backend.
  return Date.parse(/(?:Z|[+-]\d\d:\d\d)$/i.test(value) ? value : value + 'Z');
}

function piePath(start: number, end: number): string {
  const point = (angle: number) => {
    const radians = (angle * Math.PI) / 180;
    return `${120 + 100 * Math.sin(radians)},${120 - 100 * Math.cos(radians)}`;
  };
  if (end - start >= 360 - 1e-10) {
    return 'M120,20 A100,100 0 1,1 120,220 A100,100 0 1,1 120,20 Z';
  }
  return `M120,120 L${point(start)} A100,100 0 ${end - start > 180 ? 1 : 0},1 ${point(end)} Z`;
}

/** Estimate allocation by market value, with every amount expressed in one currency. */
export function buildHoldingsAllocation(
  positions: readonly PositionResponse[],
  instruments: readonly MarketInstrumentResponse[],
  accountCurrency: string,
  rates: Readonly<Record<string, FxQuote>>,
  now = Date.now(),
): HoldingsAllocation {
  const result: HoldingsAllocation = {
    slices: [],
    total: 0,
    holdingCount: 0,
    missing: [],
    stale: false,
    oldestQuote: null,
  };
  const market = new Map(instruments.map((instrument) => [instrument.instrumentId, instrument]));
  let oldest = Infinity;
  for (const position of positions) {
    if (position.quantity === 0) continue;
    result.holdingCount++;
    const instrument = market.get(position.instrumentId);
    const quote = instrument?.latestQuote;
    const conversion = instrument ? rates[instrument.currency] : undefined;
    const rate =
      instrument?.currency === accountCurrency
        ? 1
        : conversion?.from === instrument?.currency && conversion?.to === accountCurrency
          ? conversion.rate
          : NaN;
    const value = position.quantity * (quote?.lastPrice ?? NaN) * rate;
    if (
      !accountCurrency ||
      !Number.isFinite(position.quantity) ||
      position.quantity <= 0 ||
      !quote ||
      quote.instrumentId !== position.instrumentId ||
      !Number.isFinite(quote.lastPrice) ||
      quote.lastPrice <= 0 ||
      !Number.isFinite(rate) ||
      rate <= 0 ||
      !Number.isFinite(value) ||
      value <= 0
    ) {
      result.missing.push(position.symbol);
      continue;
    }
    const quotedAt = timestamp(quote.quotedAt);
    result.stale ||= !Number.isFinite(quotedAt) || now - quotedAt > 10_000;
    if (Number.isFinite(quotedAt) && quotedAt < oldest) {
      oldest = quotedAt;
      result.oldestQuote = new Date(quotedAt).toISOString();
    }
    result.slices.push({
      ...position,
      value,
      share: 0,
      path: '',
      color: `hsl(${((position.instrumentId * 137.508 + 136) % 360).toFixed(2)} var(--allocation-saturation, 42%) var(--allocation-lightness, 42%))`,
    });
    result.total += value;
  }
  if (!Number.isFinite(result.total)) {
    result.missing.push(...result.slices.map((slice) => slice.symbol));
    result.slices = [];
    result.total = 0;
    return result;
  }
  result.slices.sort((a, b) => b.value - a.value || a.instrumentId - b.instrumentId);
  let angle = 0;
  result.slices.forEach((slice, index) => {
    slice.share = (slice.value / result.total) * 100;
    const end = index === result.slices.length - 1 ? 360 : angle + slice.share * 3.6;
    slice.path = piePath(angle, end);
    angle = end;
  });
  return result;
}
