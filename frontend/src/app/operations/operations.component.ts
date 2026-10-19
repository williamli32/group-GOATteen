import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import {
    OperationsAuditResponse,
    OperationsFilters,
    OperationsOrder,
    OperationsService
} from '../core/services/operations';

@Component({
    selector: 'app-operations',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './operations.component.html',
    styleUrl: './operations.component.scss'
})
export class OperationsComponent implements OnInit, OnDestroy {
    readonly today = new Date().toISOString().slice(0, 10);
    readonly fromDate = signal(this.dateDaysAgo(30));
    readonly toDate = signal(this.today);
    readonly orderId = signal<number | undefined>(undefined);
    readonly clientFirstName = signal('');
    readonly clientLastName = signal('');
    readonly accountId = signal<number | undefined>(undefined);
    readonly instrumentSymbol = signal('');
    readonly side = signal<OperationsFilters['side'] | undefined>(undefined);
    readonly status = signal<string | undefined>(undefined);
    readonly orders = signal<OperationsOrder[]>([]);
    readonly selectedOrder = signal<OperationsOrder | null>(null);
    readonly audit = signal<OperationsAuditResponse | null>(null);
    readonly loading = signal(false);
    readonly detailLoading = signal(false);
    readonly errorMessage = signal('');
    readonly detailError = signal('');
    private searchRequest?: Subscription;
    private auditRequest?: Subscription;
    private searchVersion = 0;
    private auditVersion = 0;

    constructor(private readonly operationsService: OperationsService) { }

    ngOnInit(): void {
        this.search();
    }

    ngOnDestroy(): void {
        this.searchVersion++;
        this.auditVersion++;
        this.searchRequest?.unsubscribe();
        this.auditRequest?.unsubscribe();
    }

    search(): void {
        const version = ++this.searchVersion;
        this.searchRequest?.unsubscribe();
        this.clearSelection();
        this.loading.set(true);
        this.orders.set([]);
        this.errorMessage.set('');
        this.searchRequest = this.operationsService.searchOrders(this.filters()).subscribe({
            next: orders => {
                if (version !== this.searchVersion) return;
                this.orders.set(orders);
                this.loading.set(false);
            },
            error: () => {
                if (version !== this.searchVersion) return;
                this.orders.set([]);
                this.loading.set(false);
                this.errorMessage.set('Operations data could not be loaded.');
            }
        });
    }

    resetFilters(): void {
        this.fromDate.set(this.dateDaysAgo(30));
        this.toDate.set(this.today);
        this.orderId.set(undefined);
        this.clientFirstName.set('');
        this.clientLastName.set('');
        this.accountId.set(undefined);
        this.instrumentSymbol.set('');
        this.side.set(undefined);
        this.status.set(undefined);
        this.search();
    }

    inspect(order: OperationsOrder): void {
        const version = ++this.auditVersion;
        this.auditRequest?.unsubscribe();
        this.selectedOrder.set(order);
        this.audit.set(null);
        this.detailError.set('');
        this.detailLoading.set(true);
        this.auditRequest = this.operationsService.inspectOrder(order.orderId).subscribe({
            next: response => {
                if (version !== this.auditVersion) return;
                if (response.evidence.order.orderId !== order.orderId) {
                    this.detailLoading.set(false);
                    this.detailError.set('The evidence does not match the selected order. Please retry.');
                    return;
                }
                this.audit.set(response);
                this.detailLoading.set(false);
            },
            error: () => {
                if (version !== this.auditVersion) return;
                this.detailLoading.set(false);
                this.detailError.set('The order evidence could not be loaded.');
            }
        });
    }

    clearSelection(): void {
        this.auditVersion++;
        this.auditRequest?.unsubscribe();
        this.selectedOrder.set(null);
        this.audit.set(null);
        this.detailLoading.set(false);
        this.detailError.set('');
    }

    retryInspection(): void {
        const order = this.selectedOrder();
        if (order) this.inspect(order);
    }

    setNumber(target: 'orderId' | 'accountId', value: string): void {
        this[target].set(value ? Number(value) : undefined);
    }

    setInstrumentSymbol(value: string): void { this.instrumentSymbol.set(value); }

    setClientFirstName(value: string): void { this.clientFirstName.set(value); }
    setClientLastName(value: string): void { this.clientLastName.set(value); }

    setSide(value: string): void { this.side.set(value ? value as OperationsFilters['side'] : undefined); }
    setStatus(value: string): void { this.status.set(value || undefined); }

    formatMoney(value: number | null | undefined, currency: string | null | undefined, price = false): string {
        if (value === null || value === undefined) return 'Not available';
        if (!currency) return `${this.formatNumber(value)} (currency unavailable)`;
        return new Intl.NumberFormat('en-GB', {
            style: 'currency', currency, currencyDisplay: 'code',
            maximumFractionDigits: price ? 8 : 2
        }).format(value);
    }

    formatNumber(value: number | null | undefined): string {
        return new Intl.NumberFormat('en-GB', { maximumFractionDigits: 8 }).format(value ?? 0);
    }

    private filters(): OperationsFilters {
        return {
            orderId: this.orderId(), clientFirstName: this.clientFirstName(), clientLastName: this.clientLastName(), accountId: this.accountId(),
            instrumentSymbol: this.instrumentSymbol(), side: this.side(), status: this.status(),
            fromDate: this.fromDate(), toDate: this.toDate()
        };
    }

    private dateDaysAgo(days: number): string {
        const date = new Date();
        date.setDate(date.getDate() - days);
        return date.toISOString().slice(0, 10);
    }
}
