import { CommonModule } from '@angular/common';
import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, forkJoin } from 'rxjs';
import {
    ActivityTrendPoint,
    BusinessInsights,
    ClientMetrics,
    InstrumentMetrics,
    ReportingActivity,
    ReportingFilters,
    ReportingService,
    ReportingSummary
} from '../core/services/reporting';
import {
    MarketDataService,
    MarketInstrumentResponse
} from '../core/services/market-data';

interface ClientCurrencyValue {
    currency: string;
    tradedValue: number;
}

interface AggregatedClientMetrics {
    clientId: number;
    clientName: string;
    orderCount: number;
    fillCount: number;
    values: ClientCurrencyValue[];
}

interface GraphDialog {
    date: string;
    detail: string;
    series: Array<{ label: string; value: string; color: string }>;
    x: number;
    y: number;
}

interface RejectionChartPoint {
    date: string;
    totalOrders: number;
    rejectedOrders: number;
    rejectionRate: number;
    x: number;
    y: number;
}

interface RejectionTrendChart {
    points: RejectionChartPoint[];
    ticks: Array<{ date: string; x: number; secondary: boolean }>;
    dayCount: number;
    path: string;
}

function buildRejectionTrendChart(insight: BusinessInsights | null): RejectionTrendChart {
    const chart: RejectionTrendChart = { points: [], ticks: [], dayCount: 0, path: '' };
    if (!insight) {
        return chart;
    }

    const dayMs = 86_400_000;
    const start = Date.parse(insight.currentWindow.fromDate + 'T00:00:00Z');
    const end = Date.parse(insight.currentWindow.toDate + 'T00:00:00Z');
    const dayCount = Math.round((end - start) / dayMs) + 1;
    if (!Number.isFinite(dayCount) || dayCount < 1 || dayCount > 366) {
        return chart;
    }

    chart.dayCount = dayCount;
    const byDate = new Map(insight.rejectionTrend.map(point => [point.date, point]));
    const xForDay = (index: number) => dayCount === 1 ? 50 : index / (dayCount - 1) * 100;
    const commands: string[] = [];
    let connected = false;

    // Use calendar dates, not array positions. Missing dates and no-order days
    // both break the line instead of inventing a zero rate or bridging a gap.
    for (let index = 0; index < dayCount; index++) {
        const date = new Date(start + index * dayMs).toISOString().slice(0, 10);
        const source = byDate.get(date);
        const rate = source?.rejectionRate;
        if (!source || source.totalOrders <= 0 || rate === null || rate === undefined
            || !Number.isFinite(rate) || rate < 0 || rate > 100) {
            connected = false;
            continue;
        }
        const point = { ...source, rejectionRate: rate, x: xForDay(index), y: 100 - rate };
        chart.points.push(point);
        commands.push(`${connected ? 'L' : 'M'}${point.x},${point.y}`);
        connected = true;
    }
    chart.path = commands.join(' ');

    const tickCount = Math.min(dayCount, 5);
    chart.ticks = Array.from({ length: tickCount }, (_, index) => {
        const offset = tickCount === 1 ? 0 : Math.round(index * (dayCount - 1) / (tickCount - 1));
        return {
            date: new Date(start + offset * dayMs).toISOString().slice(0, 10),
            x: xForDay(offset),
            secondary: tickCount === 5 && (index === 1 || index === 3)
        };
    });
    return chart;
}

type MetricKey =
    | 'trades'
    | 'quantity'
    | 'filled'
    | 'rejected'
    | 'value'
    | 'clients'
    | 'instruments';

@Component({
    selector: 'app-analyst-dashboard',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './analyst-dashboard.component.html',
    styleUrls: ['./analyst-dashboard.component.scss', './business-insights.scss']
})
export class AnalystDashboardComponent implements OnInit, OnDestroy, AfterViewChecked {
    readonly Math = Math;
    readonly today = new Date().toISOString().slice(0, 10);
    readonly defaultFromDate = this.dateDaysAgo(30);
    readonly fromDate = signal(this.defaultFromDate);
    readonly toDate = signal(this.today);
    readonly instrumentId = signal<number | undefined>(undefined);
    readonly side = signal<ReportingFilters['side'] | undefined>(undefined);
    readonly status = signal<string | undefined>(undefined);
    readonly loading = signal(false);
    readonly errorMessage = signal('');
    readonly currency = signal<string>('GBP');
    readonly appliedFilters = signal<ReportingFilters>({
        fromDate: this.defaultFromDate, toDate: this.today, currency: 'GBP'
    });
    readonly reportedCurrency = computed(() => this.appliedFilters().currency ?? 'GBP');
    readonly accessDenied = signal(false);
    readonly summary = signal<ReportingSummary | null>(null);
    readonly insights = signal<BusinessInsights | null>(null);
    readonly rejectionRateTicks = [100, 75, 50, 25, 0];
    readonly rejectionChart = computed(() => buildRejectionTrendChart(this.insights()));
    readonly trends = signal<ActivityTrendPoint[]>([]);
    readonly trendTicks = computed(() => {
        const points = this.trends();
        const count = Math.min(points.length, 5);
        return Array.from({ length: count }, (_, tick) => {
            const index = count === 1 ? 0 : Math.round(tick * (points.length - 1) / (count - 1));
            return {
                date: points[index].date,
                x: points.length === 1 ? 50 : index / (points.length - 1) * 100,
                secondary: count === 5 && (tick === 1 || tick === 3)
            };
        });
    });
    readonly instruments = signal<InstrumentMetrics[]>([]);
    readonly clients = signal<ClientMetrics[]>([]);
    readonly activity = signal<ReportingActivity[]>([]);
    readonly marketInstruments = signal<MarketInstrumentResponse[]>([]);
    readonly clientId = signal<number | undefined>(undefined);
    readonly clientOptions = signal<ClientMetrics[]>([]);
    readonly graphDialog = signal<GraphDialog | null>(null);
    readonly expandedMetric = signal<MetricKey | null>(null);
    @ViewChild('metricDialog') private metricDialog?: ElementRef<HTMLElement>;
    private metricTrigger: HTMLElement | null = null;
    private focusMetricDialog = false;
    private reportingRequest?: Subscription;
    private clientOptionsRequest?: Subscription;
    private marketRequest?: Subscription;
    private reportingVersion = 0;
    private clientOptionsVersion = 0;

    constructor(
        private readonly reportingService: ReportingService,
        private readonly marketDataService: MarketDataService
    ) { }

    ngOnInit(): void {
        this.loadMarketInstruments();
        this.loadClientOptions();
        this.loadReporting();
    }

    ngOnDestroy(): void {
        this.reportingVersion++;
        this.clientOptionsVersion++;
        this.reportingRequest?.unsubscribe();
        this.clientOptionsRequest?.unsubscribe();
        this.marketRequest?.unsubscribe();
    }

    ngAfterViewChecked(): void {
        if (this.focusMetricDialog && this.metricDialog) {
            this.focusMetricDialog = false;
            this.metricDialog.nativeElement.querySelector<HTMLButtonElement>('button')?.focus();
        }
    }

    selectClient(value: string): void {
        this.clientId.set(value ? Number(value) : undefined);
    }

    applyFilters(): void {
        this.loadClientOptions();
        this.loadReporting();
    }

    currencyOptions(): string[] {
        return [
            ...new Set(
                this.marketInstruments()
                    .map(instrument => instrument.currency)
            )
        ].sort();
    }

    selectCurrency(value: string): void {
        this.currency.set(value);
    }


    resetFilters(): void {
        this.fromDate.set(this.defaultFromDate);
        this.toDate.set(this.today);
        this.instrumentId.set(undefined);
        this.side.set(undefined);
        this.status.set(undefined);
        this.clientId.set(undefined);
        this.currency.set('GBP');

        this.loadClientOptions();
        this.loadReporting();
    }

    selectInstrument(value: string): void {

        const id =
            value ? Number(value) : undefined;

        this.instrumentId.set(id);

        if (id !== undefined) {

            const instrument =
                this.marketInstruments()
                    .find(item =>
                        item.instrumentId === id
                    );

            if (instrument) {
                this.currency.set(
                    instrument.currency
                );
            }
        }
    }

    selectSide(value: string): void {
        this.side.set(value ? value as ReportingFilters['side'] : undefined);
    }

    selectStatus(value: string): void {
        this.status.set(value || undefined);
    }

    trendPoints(field: 'orders' | 'value'): string {
        const points = this.trendCoordinates(field);
        return points.map(point => `${point.x},${point.y}`).join(' ');
    }

    trendAreaPoints(field: 'orders' | 'value'): string {
        const points = this.trendCoordinates(field);

        if (!points.length) {
            return '';
        }

        return [
            `${points[0].x},190`,
            ...points.map(point => `${point.x},${point.y}`),
            `${points[points.length - 1].x},190`
        ].join(' ');
    }

    trendPointX(index: number): number {
        return this.trendCoordinates('orders')[index]?.x ?? 0;
    }

    clientTrendPoints(): string {
        return this.clientTrendCoordinates()
            .map(point => `${point.x},${point.y}`)
            .join(' ');
    }

    clientTrendAreaPoints(): string {
        const points = this.clientTrendCoordinates();

        if (!points.length) {
            return '';
        }

        return [
            `${points[0].x},190`,
            ...points.map(point => `${point.x},${point.y}`),
            `${points[points.length - 1].x},190`
        ].join(' ');
    }

    clientTrendPointX(index: number): number {
        return this.clientTrendCoordinates()[index]?.x ?? 0;
    }

    clientTrendPointY(index: number): number {
        return this.clientTrendCoordinates()[index]?.y ?? 190;
    }

    buySellTrendPoints(side: 'BUY' | 'SELL'): string {
        return this.buySellTrendCoordinates(side)
            .map(point => `${point.x},${point.y}`)
            .join(' ');
    }

    buySellTrendPointX(index: number): number {
        return this.buySellTrendCoordinates('BUY')[index]?.x ?? 0;
    }

    buySellTrendPointY(index: number, side: 'BUY' | 'SELL'): number {
        return this.buySellTrendCoordinates(side)[index]?.y ?? 190;
    }

    buySellAxisLabels(): number[] {
        const maximum = Math.max(
            ...this.trends().flatMap(point => [point.buyFills, point.sellFills]),
            1
        );

        return [maximum, maximum / 2, 0];
    }

    private buySellTrendCoordinates(side: 'BUY' | 'SELL'): Array<{ x: number; y: number }> {
        const values = this.trends().map(point => side === 'BUY' ? point.buyFills : point.sellFills);
        const maximum = Math.max(
            ...this.trends().flatMap(point => [point.buyFills, point.sellFills]),
            1
        );
        const chartWidth = 600;
        const chartBottom = 190;
        const chartTop = 22;

        return values.map((value, index) => ({
            x: values.length === 1
                ? chartWidth / 2
                : (index / (values.length - 1)) * chartWidth,
            y: chartBottom - ((value / maximum) * (chartBottom - chartTop))
        }));
    }

    private clientTrendCoordinates(): Array<{ x: number; y: number }> {
        const values = this.trends().map(point => point.activeClients);
        const maximum = Math.max(...values, 1);
        const chartWidth = 600;
        const chartBottom = 190;
        const chartTop = 22;

        return values.map((value, index) => ({
            x: values.length === 1
                ? chartWidth / 2
                : (index / (values.length - 1)) * chartWidth,
            y: chartBottom - ((value / maximum) * (chartBottom - chartTop))
        }));
    }

    trendPointY(index: number, field: 'orders' | 'value'): number {
        return this.trendCoordinates(field)[index]?.y ?? 190;
    }

    showTrendDialog(
        title: string,
        date: string,
        value: string,
        detail: string,
        event: MouseEvent,
    ): void {
        this.graphDialog.set({
            date,
            detail,
            series: [{ label: title, value, color: '#72d8c0' }],
            x: event.clientX,
            y: event.clientY
        });
    }

    showBuySellDateDialog(index: number, event: MouseEvent): void {
        const point = this.trends()[index];

        if (!point) {
            return;
        }

        this.graphDialog.set({
            date: this.graphDateLabel(point.date),
            detail: 'BUY and SELL fills for this date',
            series: [
                {
                    label: 'BUY fills',
                    value: this.formatNumber(point.buyFills),
                    color: '#72d8c0'
                },
                {
                    label: 'SELL fills',
                    value: this.formatNumber(point.sellFills),
                    color: '#ee7b83'
                },
                
            ],
            x: event.clientX,
            y: event.clientY
        });
    }

    hideTrendDialog(): void {
        this.graphDialog.set(null);
    }

    graphDateLabel(date: string): string {
        return new Intl.DateTimeFormat('en-GB', {
            month: 'short',
            day: 'numeric',
            timeZone: 'UTC'
        }).format(new Date(`${date}T00:00:00Z`));
    }

    showBuySellDialog(side: 'BUY' | 'SELL', date: string, fills: number, event: MouseEvent): void {
        this.showTrendDialog(
            `${side} fills`,
            date,
            `${fills} fills`,
            'Historical daily activity',
            event,
        );
    }

    showOutcomeDialog(status: 'FILLED' | 'REJECTED', orders: number, event: MouseEvent): void {
        this.showTrendDialog(
            `${status} orders`,
            'Current reporting period',
            `${orders} orders`,
            'Order outcome total',
            event,
        );
    }

    showPieDialog(event: MouseEvent): void {
        this.showTrendDialog(
            'BUY vs SELL',
            'Current reporting period',
            `${this.formatNumber((this.summary()?.buyFills ?? 0) + (this.summary()?.sellFills ?? 0))} fills`,
            `BUY ${this.summary()?.buyFills ?? 0} · SELL ${this.summary()?.sellFills ?? 0}`,
            event,
        );
    }

    trendAxisLabels(field: 'orders' | 'value'): number[] {
        const values = this.trends().map(point => field === 'orders'
            ? point.filledOrders
            : point.tradedValue);
        const maximum = Math.max(...values, 1);
        return [maximum, maximum / 2, 0];
    }

    clientAxisLabels(): number[] {
        const maximum = Math.max(
            ...this.trends().map(point => point.activeClients),
            1
        );
        return [maximum, maximum / 2, 0];
    }

    private trendCoordinates(field: 'orders' | 'value'): Array<{ x: number; y: number }> {
        const values = this.trends().map(point => field === 'orders'
            ? point.filledOrders
            : point.tradedValue);
        const maximum = Math.max(...values, 1);
        const chartWidth = 600;
        const chartBottom = 190;
        const chartTop = 22;

        return values.map((value, index) => ({
            x: values.length === 1
                ? chartWidth / 2
                : (index / (values.length - 1)) * chartWidth,
            y: chartBottom - ((value / maximum) * (chartBottom - chartTop))
        }));
    }

    formatMoney(
        value: number | null | undefined,
        currency: string = 'GBP'
    ): string {
        return new Intl.NumberFormat('en-GB', {
            style: 'currency',
            currency,
            maximumFractionDigits: 2
        }).format(value ?? 0);
    }

    formatNumber(value: number | null | undefined): string {
        return new Intl.NumberFormat('en-GB', {
            maximumFractionDigits: 2
        }).format(value ?? 0);
    }

    formatQuantity(value: number | null | undefined): string {
        return new Intl.NumberFormat('en-GB', {
            maximumFractionDigits: 8
        }).format(value ?? 0);
    }

    unfilledOrders(): number {
        return (this.summary()?.totalOrders ?? 0)
            - (this.summary()?.filledOrders ?? 0);
    }

    splitRingStyle(): string {

        const buy =
            this.summary()?.buyFills ?? 0;

        const sell =
            this.summary()?.sellFills ?? 0;

        const total =
            buy + sell;

        if (total <= 0) {
            return '#315f65';
        }

        const buyEnd =
            (buy / total) * 100;

        return `
        radial-gradient(
            circle,
            #101c29 0 57%,
            transparent 58%
        ),
        conic-gradient(
            #72d8c0 0 ${buyEnd}%,
            #ee7b83 ${buyEnd}% 100%
        )
    `;
    }

    trackByInstrument(_: number, item: InstrumentMetrics): number {
        return item.instrumentId;
    }

    trackByClient(_: number, item: ClientMetrics): number {
        return item.clientId;
    }

    trackByActivity(_: number, item: ReportingActivity): number {
        return item.orderId;
    }

    clientName(clientId: number): string {
        return this.clients().find(client => client.clientId === clientId)?.clientName
            ?? `Client ${clientId}`;
    }

    private loadMarketInstruments(): void {
        this.marketRequest = this.marketDataService.getInstruments().subscribe({
            next: instruments => this.marketInstruments.set(instruments),
            error: () => this.marketInstruments.set([])
        });
    }

    private loadReporting(): void {
        const version = ++this.reportingVersion;
        this.reportingRequest?.unsubscribe();
        this.closeMetric();
        this.hideTrendDialog();
        this.errorMessage.set('');
        this.accessDenied.set(false);
        this.loading.set(true);

        const filters = this.currentFilters();
        this.reportingRequest = forkJoin({
            insights: this.reportingService.getInsights(filters),
            trends: this.reportingService.getTrends(filters),
            instruments: this.reportingService.getInstruments(filters),
            clients: this.reportingService.getClients(filters),
            activity: this.reportingService.getActivity(filters)
        }).subscribe({
            next: result => {
                if (version !== this.reportingVersion) return;
                this.insights.set(result.insights);
                // Cards and their comparison dialogs must use the same server snapshot.
                this.summary.set(result.insights.current);
                this.appliedFilters.set({
                    ...filters, ...result.insights.currentWindow, currency: result.insights.currency
                });
                this.trends.set(result.trends);
                this.instruments.set(result.instruments);
                this.clients.set(result.clients);
                this.activity.set(result.activity);
                this.loading.set(false);
            },
            error: response => {
                if (version !== this.reportingVersion) return;
                this.loading.set(false);
                this.accessDenied.set(response?.status === 403);
                this.errorMessage.set(this.reportingErrorMessage(response?.status));
            }
        });
    }

    private reportingErrorMessage(status: number | undefined): string {
        if (status === 401) {
            return 'Your session has expired. Sign out and sign in again.';
        }

        if (status === 403) {
            return 'Forbidden: regular clients are not permitted to access the advisor reporting view. This view is restricted to administrators and analysts.';
        }

        if (status === 400) {
            return 'Check the reporting filters. Select a currency and a date range of at most 366 days.';
        }

        if (status && status >= 500) {
            return `The reporting service returned an error (${status}). Check the backend logs and database connection.`;
        }

        return 'Reporting data could not be loaded. Check the reporting service and try again.';
    }

    private currentFilters(): ReportingFilters {
        return {
            fromDate: this.fromDate(),
            toDate: this.toDate(),
            instrumentId: this.instrumentId(),
            side: this.side(),
            status: this.status(),
            clientId: this.clientId(),
            currency: this.currency()
        };
    }

    private loadClientOptions(): void {

        const version = ++this.clientOptionsVersion;
        this.clientOptionsRequest?.unsubscribe();

        const filters: ReportingFilters = {
            fromDate: this.fromDate(),
            toDate: this.toDate(),
            instrumentId: this.instrumentId(),
            side: this.side(),
            status: this.status(),
            currency: this.currency()
        };

        this.clientOptionsRequest = this.reportingService.getClients(filters).subscribe({
            next: clients => {
                if (version === this.clientOptionsVersion) this.clientOptions.set(clients);
            },
            error: () => {
                if (version === this.clientOptionsVersion) this.clientOptions.set([]);
            }
        });
    }

    private dateDaysAgo(days: number): string {
        const date = new Date();
        date.setDate(date.getDate() - days);
        return date.toISOString().slice(0, 10);
    }

    highestValueInstruments(): InstrumentMetrics[] {

        return [...this.instruments()]
            .sort(
                (a, b) =>
                    Number(b.tradedValue) -
                    Number(a.tradedValue)
            )
            .slice(0, 6);
    }

    totalTrades(): number {

        return (this.summary()?.buyFills ?? 0)
            + (this.summary()?.sellFills ?? 0);
    }

    toggleMetric(metric: MetricKey): void {
        if (this.expandedMetric() === metric) {
            this.closeMetric();
            return;
        }
        this.metricTrigger = document.activeElement as HTMLElement | null;
        this.expandedMetric.set(metric);
        this.focusMetricDialog = true;
    }

    closeMetric(): void {
        this.expandedMetric.set(null);
        this.focusMetricDialog = false;
        this.metricTrigger?.focus();
        this.metricTrigger = null;
    }

    onMetricDialogKeydown(event: KeyboardEvent): void {
        if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            this.closeMetric();
        } else if (event.key === 'Tab' && this.metricDialog) {
            const controls = this.metricDialog.nativeElement.querySelectorAll<HTMLElement>(
                'button, a[href], input, select, textarea, [tabindex="0"]'
            );
            const first = controls[0];
            const last = controls[controls.length - 1];
            if (first && event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (last && !event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        }
    }

    metricTitle(metric: MetricKey): string {
        return {
            trades: 'Total successful trades',
            quantity: 'Executed quantity',
            filled: 'Filled orders',
            rejected: 'Rejected orders',
            value: 'Trading value',
            clients: 'Active clients',
            instruments: 'Active instruments'
        }[metric];
    }

    metricStats(metric: MetricKey): {
        current: number;
        previous: number;
        change: number | null;
        currentLabel: string;
        previousLabel: string;
    } {
        if (metric === 'instruments') {
            return {
                current: this.instruments().length,
                previous: 0,
                change: null,
                currentLabel: 'Current period',
                previousLabel: 'Historical daily series unavailable'
            };
        }

        if (metric === 'clients') {
            return {
                current: this.distinctClientCount(),
                previous: 0,
                change: null,
                currentLabel: 'Selected period',
                previousLabel: 'Distinct count'
            };
        }

        const insights = this.insights();
        if (!insights) {
            return {
                current: 0, previous: 0, change: null,
                currentLabel: 'Current period', previousLabel: 'Comparison not loaded'
            };
        }
        const valueFor = (summary: ReportingSummary): number => {
            switch (metric) {
                case 'trades': return summary.buyFills + summary.sellFills;
                case 'quantity': return Number(summary.totalExecutedQuantity);
                case 'filled': return summary.filledOrders;
                case 'rejected': return summary.rejectedOrders;
                case 'value': return Number(summary.totalTradedValue);
                default: return 0;
            }
        };
        const current = valueFor(insights.current);
        const previous = valueFor(insights.previous);
        return {
            current,
            previous,
            change: previous === 0 ? (current === 0 ? 0 : null) : ((current - previous) / previous) * 100,
            currentLabel: insights.currentWindow.fromDate + ' to ' + insights.currentWindow.toDate,
            previousLabel: insights.previousWindow.fromDate + ' to ' + insights.previousWindow.toDate
        };
    }

    metricInterpretation(metric: MetricKey): string {
        const change = this.metricStats(metric).change;
        if (change === null) {
            return 'No percentage comparison is available without a non-zero prior baseline.';
        }
        if (change === 0) {
            return 'This metric is unchanged compared with the previous period.';
        }
        return change > 0
            ? 'This metric increased compared with the previous period.'
            : 'This metric decreased compared with the previous period.';
    }

    changeLabel(value: number | null, unit = '%', missing = 'No prior baseline'): string {
        if (value === null) {
            return missing;
        }
        return (value > 0 ? '+' : '') + this.formatNumber(value) + unit;
    }

    rejectionTrendPath(): string {
        return this.rejectionChart().path;
    }

    metricValue(metric: MetricKey, value: number): string {
        if (metric === 'value') return this.formatMoney(value, this.reportedCurrency());
        return metric === 'quantity' ? this.formatQuantity(value) : this.formatNumber(value);
    }

    distinctClientCount(): number {
        return new Set(this.clients().map(client => client.clientId)).size;
    }

    mostActiveClients(): AggregatedClientMetrics[] {

        const grouped = new Map<number, AggregatedClientMetrics>();

        for (const client of this.clients()) {

            const existing = grouped.get(client.clientId);

            if (existing) {

                existing.orderCount += client.orderCount;
                existing.fillCount += client.fillCount;

                existing.values.push({
                    currency: client.currency,
                    tradedValue: Number(client.tradedValue)
                });

            } else {

                grouped.set(client.clientId, {
                    clientId: client.clientId,
                    clientName: client.clientName,
                    orderCount: client.orderCount,
                    fillCount: client.fillCount,
                    values: [{
                        currency: client.currency,
                        tradedValue: Number(client.tradedValue)
                    }]
                });
            }
        }

        return [...grouped.values()]
            .sort((a, b) => b.orderCount - a.orderCount)
            .slice(0, 6);
    }

    uniqueClientOptions(): ClientMetrics[] {

        return Array.from(
            new Map(
                this.clientOptions().map(client => [
                    client.clientId,
                    client
                ])
            ).values()
        );
    }
}
