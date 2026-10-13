import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
    ActivityTrendPoint,
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

@Component({
    selector: 'app-analyst-dashboard',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './analyst-dashboard.component.html',
    styleUrl: './analyst-dashboard.component.scss'
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
    readonly accessDenied = signal(false);
    readonly summary = signal<ReportingSummary | null>(null);
    readonly trends = signal<ActivityTrendPoint[]>([]);
    readonly instruments = signal<InstrumentMetrics[]>([]);
    readonly clients = signal<ClientMetrics[]>([]);
    readonly activity = signal<ReportingActivity[]>([]);
    readonly marketInstruments = signal<MarketInstrumentResponse[]>([]);

    constructor(
        private readonly reportingService: ReportingService,
        private readonly marketDataService: MarketDataService
    ) { }

    ngOnInit(): void {
        this.loadMarketInstruments();
        this.loadReporting();
    }

    applyFilters(): void {
        this.loadReporting();
    }

    resetFilters(): void {
        this.fromDate.set(this.defaultFromDate);
        this.toDate.set(this.today);
        this.instrumentId.set(undefined);
        this.side.set(undefined);
        this.status.set(undefined);
        this.loadReporting();
    }

    selectInstrument(value: string): void {
        this.instrumentId.set(value ? Number(value) : undefined);
    }

    selectSide(value: string): void {
        this.side.set(value ? value as ReportingFilters['side'] : undefined);
    }

    selectStatus(value: string): void {
        this.status.set(value || undefined);
    }

    trendWidth(value: number, field: 'orders' | 'value'): string {
        const values = this.trends().map(point => field === 'orders'
            ? point.totalOrders
            : point.tradedValue);
        const maximum = Math.max(...values, 1);
        return `${Math.max((value / maximum) * 100, value ? 4 : 0)}%`;
    }

    formatMoney(value: number | null | undefined): string {
        return new Intl.NumberFormat('en-GB', {
            style: 'currency',
            currency: 'USD',
            maximumFractionDigits: 2
        }).format(value ?? 0);
    }

    formatNumber(value: number | null | undefined): string {
        return new Intl.NumberFormat('en-GB', {
            maximumFractionDigits: 2
        }).format(value ?? 0);
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
            summary: this.reportingService.getSummary(filters),
            trends: this.reportingService.getTrends(filters),
            instruments: this.reportingService.getInstruments(filters),
            clients: this.reportingService.getClients(filters),
            activity: this.reportingService.getActivity(filters)
        }).subscribe({
            next: result => {
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
            status: this.status()
        };
    }

    private dateDaysAgo(days: number): string {
        const date = new Date();
        date.setDate(date.getDate() - days);
        return date.toISOString().slice(0, 10);
    }
}
