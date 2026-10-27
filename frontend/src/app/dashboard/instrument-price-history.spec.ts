import { MarketInstrumentResponse } from '../core/services/market-data';
import {
  FRESH_PRICE_MS,
  InstrumentPriceHistoryService,
  InstrumentPricePoint,
  MAX_PRICE_POINTS,
  PRICE_WINDOW_MS,
  priceQuoteTime,
  rollingPricePoints,
  samplePricePoints,
} from './instrument-price-history';

const now = Date.UTC(2026, 9, 23, 12);
function instrument(price = 100, at = now, quoteId = 1, id = 1): MarketInstrumentResponse {
  return {
    instrumentId: id,
    symbol: 'TEST',
    name: 'Test instrument',
    instrumentClass: 'EQUITY',
    exchange: 'LSE',
    countryCode: 'GB',
    currency: 'GBP',
    tradable: true,
    latestQuote: {
      quoteId,
      instrumentId: id,
      symbol: 'TEST',
      bidPrice: price,
      askPrice: price,
      lastPrice: price,
      quotedAt: new Date(at).toISOString(),
    },
  };
}

describe('Instrument price history', () => {
  let service: InstrumentPriceHistoryService;
  beforeEach(() => {
    service = new InstrumentPriceHistoryService();
  });

  it('anchors a deterministic positive 24-hour illustration to an actual quote without changing its DTO', () => {
    const item = instrument();
    const before = JSON.stringify(item);
    const history = service.observe(item, now)!;
    expect(history.points).toHaveLength(97);
    expect(history.points[0].timestamp).toBe(now - PRICE_WINDOW_MS);
    expect(
      history.points
        .slice(0, -1)
        .every((point) => point.source === 'illustrative' && point.price > 90 && point.price < 110),
    ).toBe(true);
    expect(new Set(history.points.map((point) => point.price)).size).toBeGreaterThan(20);
    expect(history.points.at(-1)).toMatchObject({
      timestamp: now,
      price: 100,
      source: 'live',
      quoteId: 1,
    });
    expect(new InstrumentPriceHistoryService().observe(item, now)?.points).toEqual(history.points);
    expect(JSON.stringify(item)).toBe(before);
  });

  it.each(['missing', 'stale', 'future', 'wrong-instrument', 'zero', 'non-finite'])(
    'waits for a usable quote when the quote is %s',
    (state) => {
      const item = instrument();
      if (state === 'missing') item.latestQuote = null;
      else if (state === 'stale')
        item.latestQuote!.quotedAt = new Date(now - FRESH_PRICE_MS - 1).toISOString();
      else if (state === 'future') item.latestQuote!.quotedAt = new Date(now + 1000).toISOString();
      else if (state === 'wrong-instrument') item.latestQuote!.instrumentId = 99;
      else item.latestQuote!.lastPrice = state === 'zero' ? 0 : Infinity;
      expect(service.observe(item, now)).toBeNull();
    },
  );

  it('appends fresh live observations without regenerating the illustrative portion', () => {
    const first = service.observe(instrument(), now)!;
    const next = service.observe(instrument(102, now + 3000, 2), now + 3000)!;
    expect(next.points.filter((point) => point.source === 'illustrative')).toEqual(
      first.points.filter((point) => point.source === 'illustrative'),
    );
    expect(
      next.points.filter((point) => point.source === 'live').map((point) => point.price),
    ).toEqual([100, 102]);
    expect(next.firstLive).toEqual(first.firstLive);
    expect(first.latest.price).toBe(100);
  });

  it('ignores duplicate IDs, out-of-order observations and older equal-time IDs', () => {
    service.observe(instrument(), now);
    const latest = service.observe(instrument(103, now + 6000, 3), now + 6000)!;
    expect(service.observe(instrument(999, now + 6000, 3), now + 6000)).toBe(latest);
    expect(service.observe(instrument(102, now + 3000, 2), now + 6000)).toBe(latest);
    expect(service.observe(instrument(102, now + 6000, 2), now + 6000)).toBe(latest);
  });

  it('uses the newest ID at an equal timestamp without drawing duplicate x coordinates', () => {
    service.observe(instrument(), now);
    const latest = service.observe(instrument(101, now, 2), now)!;
    expect(latest.points.filter((point) => point.source === 'live')).toHaveLength(1);
    expect(latest.latest.price).toBe(101);
    expect(latest.firstLive.price).toBe(100);
  });

  it('keeps separate histories across instrument and currency changes and restores earlier selections', () => {
    const first = service.observe(instrument(), now)!;
    const other = service.observe(instrument(500, now, 1, 2), now)!;
    const rupees = service.observe({ ...instrument(8000), currency: 'INR' }, now)!;
    expect(other.key).not.toBe(first.key);
    expect(other.latest.price).toBe(500);
    expect(rupees.key).not.toBe(first.key);
    expect(rupees.latest.price).toBe(8000);
    expect(service.observe(instrument(), now)).toBe(first);
  });

  it('rolls the window forward and leaves a real observation gap after a long absence', () => {
    service.observe(instrument(), now);
    const resumedAt = now + PRICE_WINDOW_MS + 3_600_000;
    const resumed = service.observe(instrument(110, resumedAt, 2), resumedAt)!;
    expect(resumed.points).toHaveLength(1);
    expect(resumed.latest.breakBefore).toBe(true);
    expect(resumed.points[0].source).toBe('live');
    expect(rollingPricePoints(resumed.points, resumedAt + PRICE_WINDOW_MS + 1)).toEqual([]);
  });

  it('bounds a long-running history while preserving the first real price and latest quote', () => {
    service.observe(instrument(), now);
    let latest = service.observe(instrument(), now)!;
    for (let i = 1; i <= 3000; i++)
      latest = service.observe(
        instrument(100 + Math.sin(i / 15), now + i * 3000, i + 1),
        now + i * 3000,
      )!;
    expect(latest.points.length).toBeLessThanOrEqual(MAX_PRICE_POINTS);
    expect(latest.firstLive.price).toBe(100);
    expect(latest.points.at(-1)?.quoteId).toBe(3001);
    expect(latest.points.some((point) => point.source === 'live' && point.quoteId === 1)).toBe(
      true,
    );
    expect(
      latest.points.every(
        (point, i, points) => i === 0 || point.timestamp > points[i - 1].timestamp,
      ),
    ).toBe(true);
  });

  it('preserves extrema, endpoints and gaps when reducing the rendered point count', () => {
    const points: InstrumentPricePoint[] = Array.from({ length: 1000 }, (_, i) => ({
      timestamp: now + i,
      price: i === 333 ? 999 : i === 666 ? 1 : 100,
      source: 'live',
      breakBefore: i === 501,
    }));
    const sampled = samplePricePoints(points, 50);
    expect(sampled.length).toBeLessThanOrEqual(50);
    expect(sampled[0].timestamp).toBe(points[0].timestamp);
    expect(sampled.at(-1)?.timestamp).toBe(points.at(-1)?.timestamp);
    expect(sampled.some((point) => point.price === 999)).toBe(true);
    expect(sampled.some((point) => point.price === 1)).toBe(true);
    expect(sampled.some((point) => point.breakBefore)).toBe(true);
  });

  it('interprets timezone-less market timestamps as UTC and respects explicit offsets', () => {
    expect(priceQuoteTime('2026-10-23T12:00:00')).toBe(now);
    expect(priceQuoteTime('2026-10-23T14:00:00+02:00')).toBe(now);
    expect(Number.isNaN(priceQuoteTime('invalid'))).toBe(true);
  });
});
