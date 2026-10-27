import {
  PortfolioLiveHistoryService,
  MAX_LIVE_PORTFOLIO_POINTS,
  LIVE_PORTFOLIO_WINDOW_MS,
} from './portfolio-live-history';

describe('Observed live portfolio history', () => {
  let history: PortfolioLiveHistoryService;
  const start = Date.UTC(2026, 9, 27, 12);
  const sample = (
    timestamp: number,
    value: number | null,
    fingerprint = String(timestamp),
    accountId = 1,
    currency = 'GBP',
  ) => history.observe({ accountId, currency, timestamp, value, fingerprint });
  beforeEach(() => {
    history = new PortfolioLiveHistoryService();
  });

  it('starts at the first actual observation and never synthesizes earlier values', () => {
    expect(sample(start, 10100)?.points).toEqual([
      { timestamp: start, value: 10100, unavailable: [], breakBefore: false },
    ]);
    expect(sample(start + 3000, 10101)?.points.map((point) => point.value)).toEqual([10100, 10101]);
  });
  it('deduplicates clock-only updates and coalesces responses in a three-second period', () => {
    const first = sample(start, 100, 'quote-1');
    expect(sample(start + 1000, 100, 'quote-1')).toBe(first);
    const next = sample(start + 1000, 101, 'quote-2')!;
    expect(next.points).toHaveLength(1);
    expect(next.points[0].value).toBe(101);
    expect(sample(start - 1000, 1, 'older')).toBe(next);
  });
  it('preserves real zero and cash-only flat values rather than fabricating movement', () => {
    sample(start, 0);
    expect(sample(start + 3000, 0)?.points.map((point) => point.value)).toEqual([0, 0]);
  });
  it('keeps missing valuations as gaps and breaks after an interrupted feed', () => {
    sample(start, 100);
    sample(start + 3000, null);
    expect(sample(start + 6000, 101)?.points.at(-1)?.breakBefore).toBe(true);
    expect(sample(start + 21000, 102)?.points.at(-1)?.breakBefore).toBe(true);
  });
  it('separates accounts and currencies', () => {
    sample(start, 100);
    sample(start, 200, 'other-account', 2);
    sample(start, 300, 'other-currency', 1, 'USD');
    expect(history.get(1, 'GBP')?.points[0].value).toBe(100);
    expect(history.get(2, 'GBP')?.points[0].value).toBe(200);
    expect(history.get(1, 'USD')?.points[0].value).toBe(300);
  });
  it('bounds visit history by time and number of observations', () => {
    for (let i = 0; i < MAX_LIVE_PORTFOLIO_POINTS + 20; i++) sample(start + i * 3000, 100 + i);
    const points = history.get(1, 'GBP')!.points;
    expect(points.length).toBeLessThanOrEqual(MAX_LIVE_PORTFOLIO_POINTS);
    expect(points.at(-1)!.timestamp - points[0].timestamp).toBeLessThanOrEqual(
      LIVE_PORTFOLIO_WINDOW_MS,
    );
  });
  it('treats nonfinite or negative values as unavailable', () => {
    expect(sample(start, NaN)?.points[0].value).toBeNull();
    expect(sample(start + 3000, -1)?.points.at(-1)?.value).toBeNull();
  });
});
