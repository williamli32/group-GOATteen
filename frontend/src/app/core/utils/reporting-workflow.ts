const DAY_MS = 86_400_000;

export function dateValue(value: unknown): number | null {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const time = Date.parse(value + 'T00:00:00Z');
  return Number.isFinite(time) && new Date(time).toISOString().slice(0, 10) === value ? time : null;
}

export function dateRangeError(from: unknown, to: unknown, required = true): string {
  if (required && (!from || !to)) return 'Choose both a start date and an end date.';
  const start = from ? dateValue(from) : null;
  const end = to ? dateValue(to) : null;
  if ((from && start === null) || (to && end === null)) return 'Enter valid dates.';
  if (start !== null && end !== null) {
    if (start > end) return 'The start date must be on or before the end date.';
    if (required && (end - start) / DAY_MS + 1 > 366)
      return 'Choose a reporting period of at most 366 days.';
  }
  return '';
}

export function sameFilters<T extends object>(left: T, right: T): boolean {
  const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
  return [...keys].every((key) => left[key as keyof T] === right[key as keyof T]);
}

export function validOptionalId(value: unknown): boolean {
  return (
    value === undefined || (typeof value === 'number' && Number.isSafeInteger(value) && value > 0)
  );
}

export function pageCount(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / size));
}

export function boundedPage(page: number, total: number, size: number): number {
  return Math.min(pageCount(total, size), Math.max(1, Math.trunc(page) || 1));
}

export function pageRows<T>(rows: T[], page: number, size: number): T[] {
  const start = (boundedPage(page, rows.length, size) - 1) * size;
  return rows.slice(start, start + size);
}

// Persist preferences only, never result rows, evidence, or access tokens.
// JWT subject scopes browser-session preferences to the signed-in user.
function preferenceKey(view: string): string | null {
  try {
    const token = localStorage.getItem('accessToken');
    if (!token) return null;
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const subject = JSON.parse(atob(payload)).sub;
    return typeof subject === 'string' && subject.length > 0 && subject.length <= 256
      ? `leap_workflow_${encodeURIComponent(subject)}_${view}`
      : null;
  } catch {
    return null;
  }
}

export function readWorkflowPreferences<T>(view: string): T | null {
  try {
    const key = preferenceKey(view);
    const value = key && sessionStorage.getItem(key);
    return value ? (JSON.parse(value) as T) : null;
  } catch {
    return null;
  }
}

export function saveWorkflowPreferences(view: string, value: unknown): void {
  try {
    const key = preferenceKey(view);
    if (key) sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* The current view remains usable when browser storage is unavailable. */
  }
}

export function recordedTimestamp(value: string | null | undefined): string {
  if (!value) return 'Not recorded';
  // A timezone-free database timestamp must not shift with the viewer's timezone.
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value)) {
    return value.replace('T', ' ').slice(0, 19);
  }
  const time = new Date(value);
  return Number.isFinite(time.getTime())
    ? time.toISOString().replace('T', ' ').slice(0, 19) + ' UTC'
    : 'Not recorded';
}

export interface DailyPlotPoint<T> {
  source: T;
  index: number;
  date: string;
  value: number;
  x: number;
  y: number;
}

export interface DailyPlot<T> {
  points: DailyPlotPoint<T>[];
  path: string;
  area: string;
  ticks: Array<{ date: string; x: number; secondary: boolean }>;
  labels: number[];
  dayCount: number;
  activeDays: number;
}

export function dailyPlot<T extends { date: string }>(
  rows: T[],
  from: string,
  to: string,
  valueFor: (row: T) => number,
  count = true,
  sharedMaximum?: number,
): DailyPlot<T> {
  const plot: DailyPlot<T> = {
    points: [],
    path: '',
    area: '',
    ticks: [],
    labels: [],
    dayCount: 0,
    activeDays: 0,
  };
  const start = dateValue(from),
    end = dateValue(to);
  if (start === null || end === null || end < start || (end - start) / DAY_MS >= 366) return plot;
  plot.dayCount = Math.round((end - start) / DAY_MS) + 1;
  const records = rows
    .map((source, index) => {
      const value = valueFor(source);
      return {
        source,
        index,
        time: dateValue(source.date),
        value: value == null ? NaN : Number(value),
      };
    })
    .filter(
      (row) =>
        row.time !== null &&
        row.time >= start &&
        row.time <= end &&
        Number.isFinite(row.value) &&
        row.value >= 0,
    )
    .sort((a, b) => a.time! - b.time!);
  const maximum = Math.max(sharedMaximum ?? 0, ...records.map((row) => row.value));
  const upper = count ? Math.max(2, Math.ceil(maximum / 2) * 2) : maximum || 1;
  plot.labels = [upper, upper / 2, 0];
  const xFor = (time: number) => (start === end ? 300 : ((time - start) / (end - start)) * 600);
  plot.points = records.map((row) => ({
    source: row.source,
    index: row.index,
    date: row.source.date,
    value: row.value,
    x: xFor(row.time!),
    y: 190 - (row.value / upper) * 168,
  }));
  plot.activeDays = plot.points.filter((point) => point.value > 0).length;
  const segments: Array<DailyPlotPoint<T>[]> = [];
  for (const point of plot.points) {
    const segment = segments[segments.length - 1];
    const previous = segment?.[segment.length - 1];
    if (!previous || dateValue(point.date)! - dateValue(previous.date)! !== DAY_MS)
      segments.push([point]);
    else segment.push(point);
  }
  plot.path = segments
    .map((segment) => segment.map((p, index) => `${index ? 'L' : 'M'}${p.x},${p.y}`).join(' '))
    .join(' ');
  plot.area = segments
    .map(
      (segment) =>
        `M${segment[0].x},190 ` +
        segment.map((p) => `L${p.x},${p.y}`).join(' ') +
        ` L${segment[segment.length - 1].x},190 Z`,
    )
    .join(' ');
  const tickCount = Math.min(plot.dayCount, 5);
  plot.ticks = Array.from({ length: tickCount }, (_, index) => {
    const offset =
      tickCount === 1 ? 0 : Math.round((index * (plot.dayCount - 1)) / (tickCount - 1));
    return {
      date: new Date(start + offset * DAY_MS).toISOString().slice(0, 10),
      x: start === end ? 50 : (offset / (plot.dayCount - 1)) * 100,
      secondary: tickCount === 5 && (index === 1 || index === 3),
    };
  });
  return plot;
}
