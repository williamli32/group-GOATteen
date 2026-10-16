import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
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
export class AnalystDashboardComponent implements OnInit {
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
    readonly accessDenied = signal(false);
    readonly summary = signal<ReportingSummary | null>(null);
    readonly insights = signal<BusinessInsights | null>(null);
    readonly trends = signal<ActivityTrendPoint[]>([]);
    readonly instruments = signal<InstrumentMetrics[]>([]);
    readonly clients = signal<ClientMetrics[]>([]);
    readonly activity = signal<ReportingActivity[]>([]);
    readonly marketInstruments = signal<MarketInstrumentResponse[]>([]);
    readonly clientId = signal<number | undefined>(undefined);
    readonly clientOptions = signal<ClientMetrics[]>([]);
    readonly graphDialog = signal<GraphDialog | null>(null);
    readonly expandedMetric = signal<MetricKey | null>(null);

    constructor(
        private readonly reportingService: ReportingService,
        private readonly marketDataService: MarketDataService
    ) { }

    ngOnInit(): void {
        this.loadMarketInstruments();
        this.loadClientOptions();
        this.loadReporting();
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
            day: 'numeric'
        }).format(new Date(`${date}T00:00:00`));
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

    showTrendLabel(index: number): boolean {
        const count = this.trends().length;
        return count <= 8 || index === 0 || index === count - 1 || index % 5 === 0;
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
        this.marketDataService.getInstruments().subscribe({
            next: instruments => this.marketInstruments.set(instruments),
            error: () => this.marketInstruments.set([])
        });
    }

    private loadReporting(): void {
        this.errorMessage.set('');
        this.accessDenied.set(false);
        this.loading.set(true);

        const filters = this.currentFilters();
        forkJoin({
            insights: this.reportingService.getInsights(filters),
            summary: this.reportingService.getSummary(filters),
            trends: this.reportingService.getTrends(filters),
            instruments: this.reportingService.getInstruments(filters),
            clients: this.reportingService.getClients(filters),
            activity: this.reportingService.getActivity(filters)
        }).subscribe({
            next: result => {
                this.insights.set(result.insights);
                this.summary.set(result.summary);
                this.trends.set(result.trends);
                this.instruments.set(result.instruments);
                this.clients.set(result.clients);
                this.activity.set(result.activity);
                this.loading.set(false);
            },
            error: response => {
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

        const filters: ReportingFilters = {
            fromDate: this.fromDate(),
            toDate: this.toDate(),
            instrumentId: this.instrumentId(),
            side: this.side(),
            status: this.status(),
            currency: this.currency()
        };

        this.reportingService.getClients(filters).subscribe({
            next: clients => this.clientOptions.set(clients),
            error: () => this.clientOptions.set([])
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
        this.expandedMetric.update(current => current === metric ? null : metric);
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
        const points = this.insights()?.rejectionTrend ?? [];
        let connected = false;
        return points.map((point, index) => {
            if (point.rejectionRate === null) {
                connected = false;
                return '';
            }
            const x = points.length === 1 ? 300 : index / (points.length - 1) * 600;
            const y = 190 - Number(point.rejectionRate) / 100 * 168;
            const command = (connected ? 'L' : 'M') + x + ',' + y;
            connected = true;
            return command;
        }).join(' ');
    }

    metricValue(metric: MetricKey, value: number): string {
        return metric === 'value'
            ? this.formatMoney(value, this.insights()?.currency ?? this.currency())
            : this.formatNumber(value);
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
