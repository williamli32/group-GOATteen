import { Injectable } from '@angular/core';
import { MarketInstrumentResponse } from '../core/services/market-data';

export const PRICE_WINDOW_MS = 24 * 60 * 60 * 1000;
export const FRESH_PRICE_MS = 10_000;
export const MAX_PRICE_POINTS = 1800;
const PREVIEW_STEPS = 96;
const MAX_INSTRUMENTS = 64;

export interface InstrumentPricePoint {
  readonly timestamp: number;
  readonly price: number;
  readonly source: 'illustrative' | 'live';
  readonly quoteId?: number;
  readonly breakBefore?: boolean;
}

export interface InstrumentPriceHistory {
  readonly key: string;
  readonly points: readonly InstrumentPricePoint[];
  readonly firstLive: InstrumentPricePoint;
  readonly latest: InstrumentPricePoint;
}

export function priceHistoryKey(instrument: MarketInstrumentResponse): string {
  return [
    instrument.instrumentId,
    instrument.symbol,
    instrument.exchange,
    instrument.currency,
  ].join(':');
}

export function priceQuoteTime(value: string | undefined): number {
  if (!value || !/^\d{4}-\d{2}-\d{2}T/.test(value)) return NaN;
  return Date.parse(/(?:Z|[+-]\d\d:\d\d)$/i.test(value) ? value : value + 'Z');
}

/** Keep extrema and endpoints, carrying any observation gaps through downsampling. */
export function samplePricePoints(
  points: readonly InstrumentPricePoint[],
  limit: number,
): InstrumentPricePoint[] {
  if (points.length <= limit) return [...points];
  const buckets = Math.max(1, Math.floor((limit - 2) / 2));
  const chosen = new Set([0, points.length - 1]);
  for (let bucket = 0; bucket < buckets; bucket++) {
    const start = 1 + Math.floor((bucket * (points.length - 2)) / buckets);
    const end = 1 + Math.floor(((bucket + 1) * (points.length - 2)) / buckets);
    let low = start,
      high = start;
    for (let i = start; i < end; i++) {
      if (points[i].price < points[low].price) low = i;
      if (points[i].price > points[high].price) high = i;
    }
    chosen.add(low);
    chosen.add(high);
  }
  let previous = -1;
  return [...chosen]
    .sort((a, b) => a - b)
    .map((index) => {
      const breakBefore = points.slice(previous + 1, index + 1).some((point) => point.breakBefore);
      previous = index;
      return breakBefore ? { ...points[index], breakBefore: true } : points[index];
    });
}

/** Include one preceding observation so the line can be clipped at the left edge. */
export function rollingPricePoints(
  points: readonly InstrumentPricePoint[],
  now: number,
  window = PRICE_WINDOW_MS,
): InstrumentPricePoint[] {
  const first = points.findIndex((point) => point.timestamp >= now - window);
  if (first === -1) return [];
  return points
    .slice(points[first].breakBefore ? first : Math.max(0, first - 1))
    .filter((point) => point.timestamp <= now);
}

function randomSource(key: string): () => number {
  let seed = 2166136261;
  for (const character of key) seed = Math.imul(seed ^ character.charCodeAt(0), 16777619) >>> 0;
  return () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 4294967296;
  };
}

function preview(
  instrument: MarketInstrumentResponse,
  anchor: InstrumentPricePoint,
): InstrumentPricePoint[] {
  const random = randomSource(priceHistoryKey(instrument) + ':' + anchor.timestamp);
  const variation =
    ({ FOREX: 0.004, CRYPTO: 0.065, COMMODITY: 0.025 } as Record<string, number>)[
      instrument.instrumentClass
    ] ?? 0.022;
  const drift = (random() - 0.5) * variation * 2;
  const walk = [0];
  for (let i = 1; i <= PREVIEW_STEPS; i++)
    walk.push(walk[i - 1] + ((random() - 0.5) * variation) / 3);
  return Array.from({ length: PREVIEW_STEPS }, (_, i) => {
    const fraction = i / PREVIEW_STEPS;
    const bridge = walk[i] - fraction * walk[PREVIEW_STEPS];
    const offset = Math.max(
      -variation * 2,
      Math.min(variation * 2, bridge + (1 - fraction) * drift),
    );
    return {
      timestamp: anchor.timestamp - PRICE_WINDOW_MS + fraction * PRICE_WINDOW_MS,
      price: anchor.price * Math.exp(offset),
      source: 'illustrative',
    };
  });
}

/** Display-only, bounded visit cache. Generated points never enter trading or reporting APIs. */
@Injectable({ providedIn: 'root' })
export class InstrumentPriceHistoryService {
  private readonly histories = new Map<string, InstrumentPriceHistory>();

  observe(instrument: MarketInstrumentResponse | null, now: number): InstrumentPriceHistory | null {
    if (!instrument) return null;
    const key = priceHistoryKey(instrument);
    const previous = this.histories.get(key) ?? null;
    const quote = instrument.latestQuote;
    const timestamp = priceQuoteTime(quote?.quotedAt);
    if (
      !quote ||
      !Number.isFinite(now) ||
      !Number.isFinite(timestamp) ||
      timestamp > now ||
      now - timestamp > FRESH_PRICE_MS ||
      quote.instrumentId !== instrument.instrumentId ||
      !Number.isSafeInteger(quote.quoteId) ||
      quote.quoteId <= 0 ||
      !Number.isFinite(quote.lastPrice) ||
      quote.lastPrice <= 0
    )
      return previous;
    if (
      previous &&
      (quote.quoteId === previous.latest.quoteId ||
        timestamp < previous.latest.timestamp ||
        (timestamp === previous.latest.timestamp && quote.quoteId <= previous.latest.quoteId!))
    )
      return previous;

    const point: InstrumentPricePoint = {
      timestamp,
      price: quote.lastPrice,
      source: 'live',
      quoteId: quote.quoteId,
      breakBefore: previous
        ? timestamp - previous.latest.timestamp > 60_000 ||
          (timestamp === previous.latest.timestamp && previous.latest.breakBefore)
        : false,
    };
    let points: InstrumentPricePoint[];
    if (!previous) points = [...preview(instrument, point), point];
    else {
      points =
        timestamp === previous.latest.timestamp
          ? [...previous.points.slice(0, -1), point]
          : [...previous.points, point];
      points = rollingPricePoints(points, now);
      if (points.length > MAX_PRICE_POINTS) {
        const illustrative = points.filter((value) => value.source === 'illustrative');
        const live = points.filter((value) => value.source === 'live');
        const recent = live.slice(-120);
        points = [
          ...illustrative,
          ...samplePricePoints(live.slice(0, -120), 1200 - illustrative.length - recent.length),
          ...recent,
        ];
      }
    }
    const result: InstrumentPriceHistory = {
      key,
      points,
      firstLive: previous?.firstLive ?? point,
      latest: point,
    };
    this.histories.delete(key);
    this.histories.set(key, result);
    if (this.histories.size > MAX_INSTRUMENTS)
      this.histories.delete(this.histories.keys().next().value!);
    return result;
  }
}
