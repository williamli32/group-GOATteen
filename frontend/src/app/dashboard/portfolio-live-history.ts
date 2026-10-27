import { Injectable } from '@angular/core';
import type { PortfolioValuePoint } from './portfolio-chart.component';

export const LIVE_PORTFOLIO_WINDOW_MS = 60 * 60 * 1000;
export const MAX_LIVE_PORTFOLIO_POINTS = 1200;
const SAMPLE_MS = 3000;

export interface LivePortfolioObservation {
  accountId: number;
  currency: string;
  timestamp: number;
  value: number | null;
  fingerprint: string;
  unavailable?: readonly string[];
}
export interface LivePortfolioHistory {
  readonly key: string;
  readonly points: readonly PortfolioValuePoint[];
}

/** Actual observations during this visit; never manufacture historical portfolio values. */
@Injectable({ providedIn: 'root' })
export class PortfolioLiveHistoryService {
  private readonly histories = new Map<string, LivePortfolioHistory & { fingerprint: string }>();

  get(accountId: number, currency: string): LivePortfolioHistory | null {
    return this.histories.get(`${accountId}:${currency}`) ?? null;
  }

  observe(observation: LivePortfolioObservation): LivePortfolioHistory | null {
    const { accountId, currency, timestamp, fingerprint } = observation;
    if (
      !Number.isSafeInteger(accountId) ||
      accountId <= 0 ||
      !/^[A-Z]{3}$/.test(currency) ||
      !Number.isFinite(timestamp) ||
      timestamp <= 0
    )
      return null;
    const key = `${accountId}:${currency}`;
    const previous = this.histories.get(key);
    const last = previous?.points.at(-1);
    if (previous && (previous.fingerprint === fingerprint || (last && timestamp < last.timestamp)))
      return previous;
    const value =
      observation.value !== null && Number.isFinite(observation.value) && observation.value >= 0
        ? observation.value
        : null;
    const point: PortfolioValuePoint = {
      timestamp,
      value,
      unavailable: value === null ? (observation.unavailable ?? ['Portfolio valuation']) : [],
      breakBefore: !!last && (last.value === null || timestamp - last.timestamp > 10_000),
    };
    let points = [...(previous?.points ?? [])];
    // Account and market responses may arrive separately within the same refresh period.
    // Retain their latest combined valuation rather than creating near-duplicate dots.
    if (last && Math.floor(timestamp / SAMPLE_MS) === Math.floor(last.timestamp / SAMPLE_MS)) {
      point.breakBefore ||= last.breakBefore || last.value === null;
      points = points.slice(0, -1);
    }
    points.push(point);
    points = points
      .filter((item) => item.timestamp >= timestamp - LIVE_PORTFOLIO_WINDOW_MS)
      .slice(-MAX_LIVE_PORTFOLIO_POINTS);
    const result = { key, points, fingerprint };
    this.histories.delete(key);
    this.histories.set(key, result);
    while (this.histories.size > 8) this.histories.delete(this.histories.keys().next().value!);
    return result;
  }
}
