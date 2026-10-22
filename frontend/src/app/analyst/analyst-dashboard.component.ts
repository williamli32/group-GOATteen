import { MarketMotionComponent } from '../shared/market-motion.component';
import { CommonModule } from '@angular/common';
import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, forkJoin } from 'rxjs';
import { ResultPaginationComponent } from '../shared/result-pagination.component';
import { boundedPage, dailyPlot, dateRangeError, pageRows, readWorkflowPreferences,
    recordedTimestamp, sameFilters, saveWorkflowPreferences, validOptionalId } from '../core/utils/reporting-workflow';
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

type ReportView = 'overview' | 'insights' | 'trends' | 'data';
type ReportTable = 'instruments' | 'values' | 'clients' | 'activity';
type DailyMetric = 'orders' | 'value' | 'clients' | 'BUY' | 'SELL';

@Component({
    selector: 'app-analyst-dashboard',
    standalone: true,
    imports: [CommonModule, FormsModule, ResultPaginationComponent, MarketMotionComponent],
    templateUrl: './analyst-dashboard.component.html',
    styleUrls: ['./analyst-dashboard.component.scss', './business-insights.scss']
})
export class AnalystDashboardComponent implements OnInit, OnDestroy, AfterViewChecked {
    readonly reportViews: ReadonlyArray<{ id: ReportView; label: string }> = [
        { id: 'overview', label: 'Overview' }, { id: 'insights', label: 'Business insights' },
        { id: 'trends', label: 'Activity & trends' }, { id: 'data', label: 'Data explorer' }
    ];
    readonly activeView = signal<ReportView>('overview');
    selectReportView(view: ReportView): void {
        this.activeView.set(view);
        this.closeMetric();
        this.hideTrendDialog();
        this.persistPreferences();
    }
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
    readonly draftChanged = computed(() => !sameFilters(this.currentFilters(), this.appliedFilters()));
    readonly filterError = signal('');
    readonly pageSize = signal(10);
    readonly pages = signal<Record<ReportTable, number>>({ instruments: 1, values: 1, clients: 1, activity: 1 });
    readonly accessDenied = signal(false);
    readonly summary = signal<ReportingSummary | null>(null);
    readonly insights = signal<BusinessInsights | null>(null);
    readonly rejectionRateTicks = [100, 75, 50, 25, 0];
    readonly rejectionChart = computed(() => buildRejectionTrendChart(this.insights()));
    readonly trends = signal<ActivityTrendPoint[]>([]);
    readonly dailyCharts = computed(() => {
        const rows = this.trends();
        const { fromDate, toDate } = this.appliedFilters();
        const buys = dailyPlot(rows, fromDate, toDate, row => row.buyFills);
        const sells = dailyPlot(rows, fromDate, toDate, row => row.sellFills);
        const maximum = Math.max(buys.labels[0] ?? 0, sells.labels[0] ?? 0);
        return {
            orders: dailyPlot(rows, fromDate, toDate, row => row.filledOrders),
            value: dailyPlot(rows, fromDate, toDate, row => row.tradedValue, false),
            clients: dailyPlot(rows, fromDate, toDate, row => row.activeClients),
            BUY: dailyPlot(rows, fromDate, toDate, row => row.buyFills, true, maximum),
            SELL: dailyPlot(rows, fromDate, toDate, row => row.sellFills, true, maximum)
        };
    });
    readonly trendTicks = computed(() => this.dailyCharts().orders.ticks);
    readonly instruments = signal<InstrumentMetrics[]>([]);
    readonly clients = signal<ClientMetrics[]>([]);
    readonly activity = signal<ReportingActivity[]>([]);
    
    readonly marketInstruments = signal<MarketInstrumentResponse[]>([]);
    readonly marketSearch = signal('');
    readonly marketClassFilter = signal('ALL');
    readonly marketClasses = [
        'ALL',
        'EQUITY',
        'FOREX',
        'CRYPTO',
        'COMMODITY'
    ] as const;
    readonly filteredMarketInstrumnets = computed(() => {
        const search = this.marketSearch().trim().toLocaleLowerCase();
        const selectedClass = this.marketClassFilter();

        return this.marketInstruments().filter(instrument => {
            const matchesSearch =
                search === ''
                || instrument.symbol.toLowerCase().includes(search)
                || instrument.name.toLowerCase().includes(search);

            const matchesClass = 
                selectedClass === 'ALL'
                || instrument.instrumentClass === selectedClass;

            return  matchesSearch && matchesClass ;
        });
    });
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
    private lastRequestedFilters: ReportingFilters | null = null;

    constructor(
        private readonly reportingService: ReportingService,
        private readonly marketDataService: MarketDataService
    ) { this.restorePreferences(); }

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
        const filters = this.currentFilters();
        this.filterError.set(this.validateFilters(filters));
        if (this.filterError()) return;
        this.loadClientOptions(filters);
        this.loadReporting(filters);
    }

    discardDraft(): void {
        this.setDraft(this.appliedFilters());
        this.filterError.set('');
        this.loadClientOptions(this.appliedFilters());
    }

    refreshApplied(): void { this.loadReporting({ ...this.appliedFilters() }); }

    retryReporting(): void {
        this.loadReporting({ ...(this.lastRequestedFilters ?? this.appliedFilters()) });
    }

    currencyOptions(): string[] {
        return ['USD', 'GBP', 'INR'];
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

        this.applyFilters();
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

    chartNote(metric: DailyMetric): string {
        const chart = this.dailyCharts()[metric];
        if (!chart.activeDays) return 'No activity for this metric in the applied period.';
        const detail = chart.points.length === 1 ? 'One daily observation; no trend can be inferred. '
            : chart.activeDays === 1 ? 'Activity occurred on one day. ' : '';
        return detail + (chart.points.length < chart.dayCount
            ? 'Gaps indicate dates with no returned observation; they are not assumed to be zero.'
            : 'Calendar days include zero-activity days.');
    }

    buySellAxisLabels(): number[] { return this.dailyCharts().BUY.labels; }

    private dialogPosition(event: Event): { x: number; y: number } {
        const bounds = (event.currentTarget as Element | null)?.getBoundingClientRect();
        const pointer = event as MouseEvent;
        return {
            x: Math.max(8, Math.min((pointer.clientX || bounds?.left || 0) + 12, window.innerWidth - 308)),
            y: Math.max(8, Math.min((pointer.clientY || bounds?.bottom || 0) + 12, window.innerHeight - 208))
        };
    }

    showTrendDialog(
        title: string,
        date: string,
        value: string,
        detail: string,
        event: Event,
        color = '#22795c',
    ): void {
        this.graphDialog.set({
            date,
            detail,
            series: [{ label: title, value, color }],
            ...this.dialogPosition(event)
        });
    }

    showDailyPoint(metric: 'orders' | 'value' | 'clients', index: number, event: Event): void {
        const point = this.dailyCharts()[metric].points[index];
        if (!point) return;
        const title = { orders: 'Filled orders', value: 'Trading value', clients: 'Active clients' }[metric];
        const detail = {
            orders: 'Distinct FILLED orders by submission day',
            value: 'Execution price × fill quantity in ' + this.reportedCurrency(),
            clients: 'Distinct clients with submitted orders on this day'
        }[metric];
        this.showTrendDialog(title, this.graphDateLabel(point.date), metric === 'value'
            ? this.formatMoney(point.value, this.reportedCurrency()) : this.formatNumber(point.value),
            detail, event, { orders: '#22795c', value: '#b88a42', clients: '#8070b5' }[metric]);
    }

    showBuySellDateDialog(index: number, event: Event): void {
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
                    color: '#22795c'
                },
                {
                    label: 'SELL fills',
                    value: this.formatNumber(point.sellFills),
                    color: '#b45a62'
                },
                
            ],
            ...this.dialogPosition(event)
        });
    }

    hideTrendDialog(): void {
        this.graphDialog.set(null);
    }

    graphDateLabel(date: string): string {
        return new Intl.DateTimeFormat('en-GB', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            timeZone: 'UTC'
        }).format(new Date(`${date}T00:00:00Z`));
    }

    showBuySellDialog(side: 'BUY' | 'SELL', date: string, fills: number, event: Event): void {
        this.showTrendDialog(
            `${side} fills`,
            date,
            `${fills} fills`,
            'Historical daily activity',
            event,
        );
    }

    showOutcomeDialog(status: 'FILLED' | 'REJECTED', orders: number, event: Event): void {
        this.showTrendDialog(
            `${status} orders`,
            'Current reporting period',
            `${orders} orders`,
            'Order outcome total',
            event,
            status === 'FILLED' ? '#22795c' : '#b45a62',
        );
    }

    showPieDialog(event: Event): void {
        this.showTrendDialog(
            'BUY vs SELL',
            'Current reporting period',
            `${this.formatNumber((this.summary()?.buyFills ?? 0) + (this.summary()?.sellFills ?? 0))} fills`,
            `BUY ${this.summary()?.buyFills ?? 0} · SELL ${this.summary()?.sellFills ?? 0}`,
            event,
        );
    }

    formatMoney(
        value: number | null | undefined,
        currency: string = 'GBP'
    ): string {
        return new Intl.NumberFormat('en-GB', {
            style: 'currency',
            currency,
            maximumFractionDigits: value && Math.abs(value) < 0.01 ? 8 : 2
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
            return '#e4ebe3';
        }

        const buyEnd =
            (buy / total) * 100;

        return `
        radial-gradient(
            circle,
            #ffffff 0 57%,
            transparent 58%
        ),
        conic-gradient(
            #22795c 0 ${buyEnd}%,
            #b45a62 ${buyEnd}% 100%
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

    readonly timestamp = recordedTimestamp;

    appliedScope(): string {
        const f = this.appliedFilters();
        const instrument = this.marketInstruments().find(item => item.instrumentId === f.instrumentId);
        return [f.fromDate + ' to ' + f.toDate + ' (inclusive)', f.currency + ' instrument currency',
            f.instrumentId ? (instrument?.symbol ?? 'Instrument #' + f.instrumentId) : 'All instruments',
            f.side ?? 'BUY and SELL', f.status ?? 'All statuses',
            f.clientId ? 'Client #' + f.clientId : 'All clients'].join(' · ');
    }

    instrumentRows(table: 'instruments' | 'values'): InstrumentMetrics[] {
        const rows = table === 'values' ? this.highestValueInstruments()
            : [...this.instruments()].sort((a, b) => b.fillCount - a.fillCount || a.instrumentId - b.instrumentId);
        return pageRows(rows, this.pages()[table], this.pageSize());
    }

    clientRows(): AggregatedClientMetrics[] { return pageRows(this.mostActiveClients(), this.pages().clients, this.pageSize()); }
    activityRows(): ReportingActivity[] { return pageRows(this.activity(), this.pages().activity, this.pageSize()); }

    tableTotal(table: ReportTable): number {
        return table === 'clients' ? this.mostActiveClients().length
            : table === 'activity' ? this.activity().length : this.instruments().length;
    }

    changePage(table: ReportTable, page: number): void {
        this.pages.update(pages => ({ ...pages, [table]: boundedPage(page, this.tableTotal(table), this.pageSize()) }));
        this.persistPreferences();
    }

    changePageSize(size: number): void {
        if (![10, 25, 50].includes(size)) return;
        this.pageSize.set(size);
        this.pages.set({ instruments: 1, values: 1, clients: 1, activity: 1 });
        this.persistPreferences();
    }

    private clampPages(): void {
        for (const table of ['instruments', 'values', 'clients', 'activity'] as const) {
            this.pages.update(pages => ({ ...pages, [table]: boundedPage(pages[table], this.tableTotal(table), this.pageSize()) }));
        }
    }

    private setDraft(filters: ReportingFilters): void {
        this.fromDate.set(filters.fromDate);
        this.toDate.set(filters.toDate);
        this.instrumentId.set(filters.instrumentId);
        this.clientId.set(filters.clientId);
        this.side.set(filters.side);
        this.status.set(filters.status);
        this.currency.set(filters.currency ?? 'GBP');
    }

    private validateFilters(filters: ReportingFilters): string {
        const dates = dateRangeError(filters.fromDate, filters.toDate);
        if (dates) return dates;
        if (!this.currencyOptions().includes(filters.currency ?? '')) return 'Choose USD, GBP or INR as the instrument currency.';
        if (!validOptionalId(filters.instrumentId) || !validOptionalId(filters.clientId)) return 'Choose a valid instrument and client.';
        if (filters.side !== undefined && !['BUY', 'SELL'].includes(filters.side)) return 'Choose BUY, SELL or both sides.';
        if (filters.status !== undefined && !['FILLED', 'REJECTED'].includes(filters.status)) return 'Choose All statuses, FILLED or REJECTED.';
        return '';
    }

    private restorePreferences(): void {
        const saved = readWorkflowPreferences<{ filters?: ReportingFilters; pageSize?: number; pages?: Partial<Record<ReportTable, number>>; view?: ReportView }>('reports');
        if (!saved || typeof saved !== 'object' || !saved.filters || typeof saved.filters !== 'object'
            || Array.isArray(saved.filters) || this.validateFilters(saved.filters)) return;
        if (saved.view && ['overview', 'insights', 'trends', 'data'].includes(saved.view)) this.activeView.set(saved.view);
        const f = saved.filters;
        const filters: ReportingFilters = { fromDate: f.fromDate, toDate: f.toDate, currency: f.currency,
            instrumentId: f.instrumentId, clientId: f.clientId, side: f.side, status: f.status };
        this.setDraft(filters);
        this.appliedFilters.set(filters);
        if (saved.pageSize && [10, 25, 50].includes(saved.pageSize)) this.pageSize.set(saved.pageSize);
        for (const table of ['instruments', 'values', 'clients', 'activity'] as const) {
            const page = saved.pages?.[table];
            if (typeof page === 'number' && Number.isSafeInteger(page) && page > 0) {
                this.pages.update(pages => ({ ...pages, [table]: page }));
            }
        }
    }

    private persistPreferences(): void {
        saveWorkflowPreferences('reports', { filters: this.appliedFilters(), pageSize: this.pageSize(), pages: this.pages(), view: this.activeView() });
    }

    private loadMarketInstruments(): void {
        this.marketRequest = this.marketDataService.getInstruments().subscribe({
            next: instruments => this.marketInstruments.set(instruments),
            error: () => this.marketInstruments.set([])
        });
    }

    private loadReporting(filters: ReportingFilters = this.currentFilters()): void {
        this.filterError.set(this.validateFilters(filters));
        if (this.filterError()) return;
        this.lastRequestedFilters = { ...filters };
        const version = ++this.reportingVersion;
        this.reportingRequest?.unsubscribe();
        this.closeMetric();
        this.hideTrendDialog();
        this.errorMessage.set('');
        this.accessDenied.set(false);
        this.loading.set(true);

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
                const scopeChanged = !sameFilters(filters, this.appliedFilters());
                this.appliedFilters.set({
                    ...filters, ...result.insights.currentWindow, currency: result.insights.currency
                });
                this.trends.set(result.trends);
                this.instruments.set(result.instruments);
                this.clients.set(result.clients);
                this.activity.set(result.activity);
                this.pages.set(scopeChanged ? { instruments: 1, values: 1, clients: 1, activity: 1 } : this.pages());
                this.clampPages();
                this.persistPreferences();
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
            return 'Forbidden: regular clients are not permitted to access the advisor reporting view. This view is restricted to administrators, analysts and operations users.';
        }

        if (status === 400) {
            return 'Check the reporting filters. Select a currency and a date range of at most 366 days.';
        }

        if (status && status >= 500) {
            return 'The reporting service is temporarily unavailable. Retry the request or try a shorter period. '
                + (this.summary() ? 'Your last successful results remain below.' : 'Your filter selections have been kept.');
        }

        return 'Reporting data could not be loaded. Check your connection and retry. '
            + (this.summary() ? 'Your filters and last successful results have been kept.' : 'Your filter selections have been kept.');
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

    private loadClientOptions(requested: ReportingFilters = this.currentFilters()): void {
        const version = ++this.clientOptionsVersion;
        this.clientOptionsRequest?.unsubscribe();
        const { clientId, ...filters } = requested;

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
            );
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

    metricDefinition(metric: MetricKey): string {
        return {
            trades: 'Successful trades count persisted fills by execution day. Filled orders use submission dates, so totals can differ when an order executes in a different reporting period.',
            quantity: 'Executed quantity sums fill quantities in instrument units. Units across different instruments are not equivalent.',
            filled: 'Filled orders count distinct orders whose current status is FILLED, using the submission date.',
            rejected: 'Rejected orders count distinct orders whose current status is REJECTED, using the submission date.',
            value: 'Trading value is execution price × fill quantity in the selected instrument currency. It is not the converted account cash settlement.',
            clients: 'Active clients are distinct clients with order activity in the period. A client active on several days is counted once for the period.',
            instruments: 'Active instruments are distinct instruments with execution fills in the applied period and currency.'
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
                case 'clients': return summary.activeClients;
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
            .sort((a, b) => b.orderCount - a.orderCount || a.clientId - b.clientId);
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
