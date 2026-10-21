import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  Injector,
  OnDestroy,
  OnInit,
  ViewChild,
  afterNextRender,
  computed,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ResultPaginationComponent } from '../shared/result-pagination.component';
import {
  boundedPage,
  dateRangeError,
  pageRows,
  readWorkflowPreferences,
  recordedTimestamp,
  sameFilters,
  saveWorkflowPreferences,
  validOptionalId,
} from '../core/utils/reporting-workflow';
import {
  OperationsAuditResponse,
  OperationsFilters,
  OperationsOrder,
  OperationsService,
} from '../core/services/operations';

type EvidenceSection = 'lifecycle' | 'execution' | 'settlement';

@Component({
  selector: 'app-operations',
  standalone: true,
  imports: [CommonModule, FormsModule, ResultPaginationComponent],
  templateUrl: './operations.component.html',
  styleUrl: './operations.component.scss',
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
  readonly appliedFilters = signal<OperationsFilters>({
    fromDate: this.fromDate(),
    toDate: this.toDate(),
  });
  readonly draftChanged = computed(() => !sameFilters(this.filters(), this.appliedFilters()));
  readonly filterError = signal('');
  readonly orders = signal<OperationsOrder[]>([]);
  readonly hasSearched = signal(false);
  readonly page = signal(1);
  readonly pageSize = signal(10);
  readonly visibleOrders = computed(() => pageRows(this.orders(), this.page(), this.pageSize()));
  readonly selectedOrder = signal<OperationsOrder | null>(null);
  readonly selectedIndex = computed(() =>
    this.orders().findIndex((row) => row.orderId === this.selectedOrder()?.orderId),
  );
  readonly audit = signal<OperationsAuditResponse | null>(null);
  readonly evidenceSection = signal<EvidenceSection>('lifecycle');
  readonly loading = signal(false);
  readonly detailLoading = signal(false);
  readonly errorMessage = signal('');
  readonly detailError = signal('');
  readonly timestamp = recordedTimestamp;
  @ViewChild('investigationPanel') private investigationPanel?: ElementRef<HTMLElement>;
  private restoredOrderId?: number;
  private lastRequestedFilters?: OperationsFilters;
  private searchRequest?: Subscription;
  private auditRequest?: Subscription;
  private searchVersion = 0;
  private auditVersion = 0;

  constructor(
    private readonly operationsService: OperationsService,
    private readonly element: ElementRef<HTMLElement>,
    private readonly injector: Injector,
  ) {
    this.restorePreferences();
  }

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
    this.loadOrders(this.filters());
  }
  refreshApplied(): void {
    this.loadOrders({ ...this.appliedFilters() });
  }
  retrySearch(): void {
    this.loadOrders({ ...(this.lastRequestedFilters ?? this.appliedFilters()) });
  }
  discardDraft(): void {
    this.setDraft(this.appliedFilters());
    this.filterError.set('');
  }

  findOrderAcrossDates(): void {
    const id = this.orderId();
    if (id === undefined || !validOptionalId(id)) {
      this.filterError.set('Enter a positive whole order ID to search across all dates.');
      return;
    }
    this.setDraft({ orderId: id });
    this.search();
  }

  private loadOrders(filters: OperationsFilters): void {
    this.filterError.set(this.validateFilters(filters));
    if (this.filterError()) return;
    const version = ++this.searchVersion;
    this.searchRequest?.unsubscribe();
    this.lastRequestedFilters = { ...filters };
    this.loading.set(true);
    this.errorMessage.set('');
    this.searchRequest = this.operationsService.searchOrders(filters).subscribe({
      next: (orders) => {
        if (version !== this.searchVersion) return;
        const scopeChanged = !sameFilters(filters, this.appliedFilters());
        this.orders.set(orders);
        this.appliedFilters.set({ ...filters });
        this.hasSearched.set(true);
        this.page.set(boundedPage(scopeChanged ? 1 : this.page(), orders.length, this.pageSize()));
        if (
          scopeChanged ||
          (this.selectedOrder() &&
            !orders.some((row) => row.orderId === this.selectedOrder()!.orderId))
        ) {
          this.clearSelection();
        }
        if (scopeChanged) this.restoredOrderId = undefined;
        if (this.restoredOrderId !== undefined) {
          const selected = orders.find((row) => row.orderId === this.restoredOrderId);
          this.restoredOrderId = undefined;
          if (selected) {
            this.page.set(Math.floor(orders.indexOf(selected) / this.pageSize()) + 1);
            this.inspect(selected, false, false);
          }
        }
        this.loading.set(false);
        this.persistPreferences();
      },
      error: (response) => {
        if (version !== this.searchVersion) return;
        this.loading.set(false);
        this.errorMessage.set(this.requestError(response?.status, false));
      },
    });
  }

  resetFilters(): void {
    this.setDraft({ fromDate: this.dateDaysAgo(30), toDate: this.today });
    this.search();
  }

  inspect(order: OperationsOrder, keepSection = false, moveFocus = true): void {
    const version = ++this.auditVersion;
    this.auditRequest?.unsubscribe();
    this.selectedOrder.set(order);
    this.audit.set(null);
    this.detailError.set('');
    this.detailLoading.set(true);
    if (!keepSection) this.evidenceSection.set('lifecycle');
    if (moveFocus)
      afterNextRender(
        () => {
          if (version !== this.auditVersion) return;
          this.investigationPanel?.nativeElement.focus();
          this.investigationPanel?.nativeElement.scrollIntoView?.({ block: 'nearest' });
        },
        { injector: this.injector },
      );
    this.persistPreferences();
    this.auditRequest = this.operationsService.inspectOrder(order.orderId).subscribe({
      next: (response) => {
        if (version !== this.auditVersion) return;
        const evidence = response.evidence;
        if (
          evidence.order.orderId !== order.orderId ||
          response.integrity.orderId !== order.orderId ||
          evidence.account.accountId !== order.accountId ||
          evidence.instrument.instrumentId !== order.instrumentId
        ) {
          this.detailLoading.set(false);
          this.detailError.set(
            'The evidence does not match the selected order or account. Retry the investigation; use the order ID shown in the results.',
          );
          return;
        }
        this.audit.set(response);
        this.detailLoading.set(false);
      },
      error: (response) => {
        if (version !== this.auditVersion) return;
        this.detailLoading.set(false);
        this.detailError.set(this.requestError(response?.status, true));
      },
    });
  }

  clearSelection(): void {
    this.auditVersion++;
    this.auditRequest?.unsubscribe();
    this.selectedOrder.set(null);
    this.audit.set(null);
    this.detailLoading.set(false);
    this.detailError.set('');
    this.persistPreferences();
  }

  returnToResults(): void {
    const id = this.selectedOrder()?.orderId;
    const index = this.selectedIndex();
    if (index >= 0) this.page.set(Math.floor(index / this.pageSize()) + 1);
    this.clearSelection();
    const version = this.auditVersion;
    afterNextRender(
      () => {
        if (version !== this.auditVersion) return;
        const control =
          this.element.nativeElement.querySelector<HTMLButtonElement>(`[data-order-id="${id}"]`) ??
          this.element.nativeElement.querySelector<HTMLButtonElement>('.inspect-button');
        control?.focus();
        control?.scrollIntoView?.({ block: 'nearest' });
      },
      { injector: this.injector },
    );
  }

  inspectAdjacent(offset: number): void {
    const index = this.selectedIndex() + offset;
    if (this.selectedIndex() < 0 || index < 0 || index >= this.orders().length) return;
    this.page.set(Math.floor(index / this.pageSize()) + 1);
    this.inspect(this.orders()[index]);
  }

  retryInspection(): void {
    const order = this.selectedOrder();
    if (order) this.inspect(order, true);
  }

  changePage(page: number): void {
    this.page.set(boundedPage(page, this.orders().length, this.pageSize()));
    this.persistPreferences();
  }
  changePageSize(size: number): void {
    if (![10, 25, 50].includes(size)) return;
    this.pageSize.set(size);
    this.page.set(1);
    this.persistPreferences();
  }

  appliedScope(): string {
    const f = this.appliedFilters();
    return [
      f.orderId ? 'Order #' + f.orderId : 'All orders',
      f.fromDate || f.toDate
        ? (f.fromDate ?? 'Beginning') + ' to ' + (f.toDate ?? 'Latest') + ' (inclusive)'
        : 'All dates',
      [f.clientFirstName, f.clientLastName].filter(Boolean).join(' ') || 'All clients',
      f.accountId ? 'Account #' + f.accountId : 'All accounts',
      f.instrumentSymbol ?? 'All symbols',
      f.side ?? 'BUY and SELL',
      f.status ?? 'All statuses',
    ].join(' · ');
  }

  investigationTitle(result: OperationsAuditResponse): string {
    return result.pending
      ? 'In progress — final checks pending'
      : result.integrity.valid && result.evidence.fullyReconstructable
        ? 'Evidence reconciled'
        : 'Evidence needs review';
  }
  investigationGuidance(result: OperationsAuditResponse): string {
    if (result.pending)
      return 'This order has not reached a final outcome. Review its recorded lifecycle, then refresh the evidence. A fill or settlement may not exist yet.';
    if (result.integrity.valid && result.evidence.fullyReconstructable) {
      return result.evidence.currentStatus === 'REJECTED'
        ? 'The rejected outcome is consistent with the persisted records. Review the rejection reason and confirm there was no trade settlement.'
        : 'The trade reconciles with the persisted records. Compare the recorded quote, execution, cash ledger and holdings with the disputed details.';
    }
    const message = result.integrity.message.toLowerCase();
    if (/lifecycle|status/.test(message))
      return 'The recorded status sequence is incomplete or inconsistent. Review the Lifecycle evidence and refresh to check for an update. Keep this order ID as the investigation reference.';
    if (/position|holding/.test(message))
      return 'The holdings evidence does not reconcile. Compare the before and after quantities in Cash & holdings, then refresh the evidence.';
    if (/cash|settlement|balance/.test(message))
      return 'The cash evidence does not reconcile. Compare the settlement amount and ledger entries in Cash & holdings, then refresh the evidence.';
    if (/fill|quote|execution/.test(message))
      return 'Execution evidence is missing or inconsistent. Review Execution & quote, compare the quote and fill IDs, then refresh the evidence.';
    return 'The available evidence could not be fully reconciled. Review each evidence section and refresh. Keep the order and account IDs with the disputed details for follow-up.';
  }

  setNumber(target: 'orderId' | 'accountId', value: string): void {
    this[target].set(value ? Number(value) : undefined);
  }
  setInstrumentSymbol(value: string): void {
    this.instrumentSymbol.set(value);
  }
  setClientFirstName(value: string): void {
    this.clientFirstName.set(value);
  }
  setClientLastName(value: string): void {
    this.clientLastName.set(value);
  }
  setSide(value: string): void {
    this.side.set(value ? (value as OperationsFilters['side']) : undefined);
  }
  setStatus(value: string): void {
    this.status.set(value || undefined);
  }

  formatMoney(
    value: number | null | undefined,
    currency: string | null | undefined,
    price = false,
  ): string {
    if (value === null || value === undefined) return 'Not available';
    if (!currency) return `${this.formatNumber(value)} (currency unavailable)`;
    return new Intl.NumberFormat('en-GB', {
      style: 'currency',
      currency,
      currencyDisplay: 'code',
      maximumFractionDigits: price || (value !== 0 && Math.abs(value) < 0.01) ? 8 : 2,
    }).format(value);
  }
  formatNumber(value: number | null | undefined): string {
    return new Intl.NumberFormat('en-GB', { maximumFractionDigits: 8 }).format(value ?? 0);
  }

  private requestError(status: number | undefined, detail: boolean): string {
    if (status === 401)
      return 'Your session has expired. Sign out and sign in again, then reopen Operations.';
    if (status === 403)
      return 'This account cannot access Operations. Sign in with an operations account.';
    if (status === 404 && detail)
      return 'This order is no longer available. Return to the results and search again using its order ID.';
    if (status === 400)
      return 'Check the order/account IDs and date range, then search again. Your entered values have been kept.';
    return detail
      ? 'The order evidence could not be loaded. Retry the investigation; your order selection has been kept.'
      : 'Operations data could not be loaded. Check your connection and retry. ' +
          (this.hasSearched()
            ? 'Your filters and last successful results have been kept.'
            : 'Your filter selections have been kept.');
  }

  private filters(): OperationsFilters {
    return {
      orderId: this.orderId(),
      clientFirstName: this.clientFirstName().trim() || undefined,
      clientLastName: this.clientLastName().trim() || undefined,
      accountId: this.accountId(),
      instrumentSymbol: this.instrumentSymbol().trim().toUpperCase() || undefined,
      side: this.side(),
      status: this.status(),
      fromDate: this.fromDate() || undefined,
      toDate: this.toDate() || undefined,
    };
  }
  private validateFilters(f: OperationsFilters): string {
    const dates = dateRangeError(f.fromDate, f.toDate, false);
    if (dates) return dates;
    if (!validOptionalId(f.orderId) || !validOptionalId(f.accountId))
      return 'Order ID and account ID must be positive whole numbers.';
    if (f.side !== undefined && !['BUY', 'SELL'].includes(f.side))
      return 'Choose BUY, SELL or both sides.';
    if (f.status !== undefined && !['FILLED', 'REJECTED'].includes(f.status))
      return 'Choose All statuses, FILLED or REJECTED.';
    if (
      [f.clientFirstName, f.clientLastName, f.instrumentSymbol].some(
        (value) => value !== undefined && typeof value !== 'string',
      )
    )
      return 'Enter valid client names and a symbol.';
    return '';
  }
  private setDraft(f: OperationsFilters): void {
    this.orderId.set(f.orderId);
    this.accountId.set(f.accountId);
    this.clientFirstName.set(f.clientFirstName ?? '');
    this.clientLastName.set(f.clientLastName ?? '');
    this.instrumentSymbol.set(f.instrumentSymbol ?? '');
    this.side.set(f.side);
    this.status.set(f.status);
    this.fromDate.set(f.fromDate ?? '');
    this.toDate.set(f.toDate ?? '');
  }
  private restorePreferences(): void {
    const saved = readWorkflowPreferences<{
      filters?: OperationsFilters;
      page?: number;
      pageSize?: number;
      selectedOrderId?: number;
    }>('operations');
    if (
      !saved ||
      typeof saved !== 'object' ||
      !saved.filters ||
      typeof saved.filters !== 'object' ||
      Array.isArray(saved.filters) ||
      this.validateFilters(saved.filters)
    )
      return;
    const f = saved.filters;
    this.setDraft({
      orderId: f.orderId,
      clientFirstName: f.clientFirstName,
      clientLastName: f.clientLastName,
      accountId: f.accountId,
      instrumentSymbol: f.instrumentSymbol,
      side: f.side,
      status: f.status,
      fromDate: f.fromDate,
      toDate: f.toDate,
    });
    this.appliedFilters.set(this.filters());
    if (saved.pageSize && [10, 25, 50].includes(saved.pageSize)) this.pageSize.set(saved.pageSize);
    if (typeof saved.page === 'number' && Number.isSafeInteger(saved.page) && saved.page > 0)
      this.page.set(saved.page);
    if (validOptionalId(saved.selectedOrderId)) this.restoredOrderId = saved.selectedOrderId;
  }
  private persistPreferences(): void {
    if (this.hasSearched())
      saveWorkflowPreferences('operations', {
        filters: this.appliedFilters(),
        page: this.page(),
        pageSize: this.pageSize(),
        selectedOrderId: this.selectedOrder()?.orderId,
      });
  }
  private dateDaysAgo(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date.toISOString().slice(0, 10);
  }
}
