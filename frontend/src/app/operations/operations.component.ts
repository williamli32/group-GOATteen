import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
export class OperationsComponent implements OnInit {
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

    constructor(private readonly operationsService: OperationsService) { }

    ngOnInit(): void {
        this.search();
    }

    search(): void {
        this.loading.set(true);
        this.errorMessage.set('');
        this.operationsService.searchOrders(this.filters()).subscribe({
            next: orders => {
                this.orders.set(orders);
                this.loading.set(false);
            },
            error: () => {
                this.orders.set([]);
                this.loading.set(false);
                this.errorMessage.set('Operations data could not be loaded.');
            }
        });
    }

    inspect(order: OperationsOrder): void {
        this.selectedOrder.set(order);
        this.audit.set(null);
        this.detailLoading.set(true);
        this.operationsService.inspectOrder(order.orderId).subscribe({
            next: response => {
                this.audit.set(response);
                this.detailLoading.set(false);
            },
            error: () => {
                this.detailLoading.set(false);
                this.errorMessage.set('The order evidence could not be loaded.');
            }
        });
    }

    clearSelection(): void {
        this.selectedOrder.set(null);
        this.audit.set(null);
    }

    setNumber(target: 'orderId' | 'accountId', value: string): void {
        this[target].set(value ? Number(value) : undefined);
    }

    setInstrumentSymbol(value: string): void { this.instrumentSymbol.set(value); }

    setClientFirstName(value: string): void { this.clientFirstName.set(value); }
    setClientLastName(value: string): void { this.clientLastName.set(value); }

    setSide(value: string): void { this.side.set(value ? value as OperationsFilters['side'] : undefined); }
    setStatus(value: string): void { this.status.set(value || undefined); }

    formatMoney(value: number | null | undefined): string {
        return new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(value ?? 0);
    }

    formatNumber(value: number | null | undefined): string {
        return new Intl.NumberFormat('en-GB', { maximumFractionDigits: 4 }).format(value ?? 0);
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
