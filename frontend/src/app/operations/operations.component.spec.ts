import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { OperationsComponent } from './operations.component';
import { OperationsAuditResponse, OperationsOrder, OperationsService } from '../core/services/operations';

describe('OperationsComponent', () => {
    let component: OperationsComponent;
    let fixture: ComponentFixture<OperationsComponent>;
    let operationsService: {
        searchOrders: ReturnType<typeof vi.fn>;
        inspectOrder: ReturnType<typeof vi.fn>;
    };

    const order = (id: number, symbol = 'MSFT'): OperationsOrder => ({
        orderId: id, clientId: 1, clientName: 'Test Client', accountId: 10, accountNumber: 'ACC-10',
        instrumentId: 7, instrumentSymbol: symbol, side: 'BUY', status: 'FILLED',
        quantity: 0.004, submittedAt: '2026-10-01T12:00:00'
    });

    const audit = (id: number, symbol = 'MSFT'): OperationsAuditResponse => ({
        evidence: {
            order: { orderId: id, side: 'BUY', quantity: 0.004, submittedAt: '2026-10-01T12:00:00' },
            account: { accountId: 10, currency: 'GBP' },
            instrument: { instrumentId: 7, symbol, currency: 'USD' },
            currentStatus: 'FILLED', statusHistory: [], rejectionReason: null,
            completedAt: '2026-10-01T12:00:01', fullyReconstructable: true,
            execution: {
                fillId: id, fillQuantity: 0.004, executionPrice: 0.000123,
                executedAt: '2026-10-01T12:00:01', quoteId: 9, bidPrice: 0.00012,
                askPrice: 0.000123, quotedAt: '2026-10-01T12:00:00'
            },
            cashImpact: { transactions: [], totalMovement: -10, balanceAfter: 100 },
            positionImpact: { quantityBefore: 0, quantityAfter: 0.004, quantityChange: 0.004, recordedAt: '2026-10-01T12:00:01' }
        },
        integrity: { orderId: id, valid: true, message: 'Trade integrity verified' }, pending: false
    });

    beforeEach(async () => {
        operationsService = {
            searchOrders: vi.fn(() => of([])),
            inspectOrder: vi.fn()
        };

        await TestBed.configureTestingModule({
            imports: [OperationsComponent],
            providers: [{ provide: OperationsService, useValue: operationsService }]
        }).compileComponents();

        fixture = TestBed.createComponent(OperationsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('loads the order blotter with the selected date range', () => {
        expect(operationsService.searchOrders).toHaveBeenCalledWith(
            expect.objectContaining({
                fromDate: component.fromDate(),
                toDate: component.toDate()
            })
        );
    });

    it('supports numeric and status filters', () => {
        component.setNumber('orderId', '42');
        component.setSide('BUY');
        component.setStatus('FILLED');
        component.search();

        expect(operationsService.searchOrders).toHaveBeenLastCalledWith(
            expect.objectContaining({ orderId: 42, side: 'BUY', status: 'FILLED' })
        );
    });
    it('cancels earlier investigations so the selection and audit cannot disagree', () => {
        const first = new Subject<OperationsAuditResponse>();
        const second = new Subject<OperationsAuditResponse>();
        operationsService.inspectOrder.mockReturnValueOnce(first).mockReturnValueOnce(second);
        component.inspect(order(1, 'AAPL'));
        component.inspect(order(2));
        expect(first.observed).toBe(false);
        second.next(audit(2));
        first.next(audit(1, 'AAPL'));
        fixture.detectChanges();
        expect(component.selectedOrder()?.orderId).toBe(2);
        expect(component.audit()?.evidence.order.orderId).toBe(2);
        expect(fixture.nativeElement.querySelector('.detail-heading').textContent).toContain('MSFT');
    });

    it('rejects evidence for an unexpected order ID', () => {
        operationsService.inspectOrder.mockReturnValue(of(audit(99)));
        component.inspect(order(2));
        expect(component.audit()).toBeNull();
        expect(component.detailError()).toContain('does not match');
    });

    it('shows an investigation error with retry and close controls', () => {
        operationsService.inspectOrder.mockReturnValueOnce(throwError(() => ({ status: 500 })))
            .mockReturnValueOnce(of(audit(2)));
        component.inspect(order(2));
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        expect(host.querySelector('.detail-error')?.textContent).toContain('could not be loaded');
        expect(host.querySelector('[aria-label="Close investigation"]')).not.toBeNull();
        expect(component.errorMessage()).toBe('');
        (host.querySelector('.detail-error button') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.detailError()).toBe('');
        expect(component.audit()?.evidence.order.orderId).toBe(2);
    });

    it('cancels an investigation when it is closed while loading', () => {
        const pending = new Subject<OperationsAuditResponse>();
        operationsService.inspectOrder.mockReturnValue(pending);
        component.inspect(order(2));
        component.clearSelection();
        pending.next(audit(2));
        expect(pending.observed).toBe(false);
        expect(component.selectedOrder()).toBeNull();
        expect(component.audit()).toBeNull();
        expect(component.detailLoading()).toBe(false);
    });

    it('cancels an older search when Reset starts another search', () => {
        const first = new Subject<OperationsOrder[]>();
        const second = new Subject<OperationsOrder[]>();
        operationsService.searchOrders.mockReturnValueOnce(first).mockReturnValueOnce(second);
        component.search();
        component.resetFilters();
        second.next([order(2)]);
        first.next([order(1)]);
        expect(first.observed).toBe(false);
        expect(component.orders().map(item => item.orderId)).toEqual([2]);
    });

    it('renders instrument and account currencies with precise execution quantities and prices', () => {
        operationsService.inspectOrder.mockReturnValue(of(audit(2)));
        component.inspect(order(2));
        fixture.detectChanges();
        const text = fixture.nativeElement.querySelector('.detail-panel').textContent;
        expect(text).toContain('USD');
        expect(text).toContain('GBP');
        expect(text).toContain('0.000123');
        expect(text).toContain('0.004');
        expect(component.formatMoney(null, 'USD')).toBe('Not available');
        expect(component.formatMoney(12, undefined)).toContain('currency unavailable');
    });

    it('shows a loading state instead of an empty result while searching', () => {
        operationsService.searchOrders.mockReturnValue(new Subject<OperationsOrder[]>());
        component.search();
        fixture.detectChanges();
        const text = fixture.nativeElement.querySelector('.blotter-panel').textContent;
        expect(text).toContain('Loading orders');
        expect(text).not.toContain('No orders match');
    });

    it('provides a native investigation button and starts only one request per click', () => {
        operationsService.inspectOrder.mockReturnValue(of(audit(2)));
        component.orders.set([order(2)]);
        fixture.detectChanges();
        const button = fixture.nativeElement.querySelector('.inspect-button') as HTMLButtonElement;
        expect(button.type).toBe('button');
        expect(button.getAttribute('aria-label')).toContain('Inspect order 2');
        button.click();
        expect(operationsService.inspectOrder).toHaveBeenCalledTimes(1);
    });
    it('finds an exact order across all dates only when the explicit action is chosen', () => {
        component.orderId.set(42);
        component.clientFirstName.set('A different client');
        component.status.set('REJECTED');
        component.findOrderAcrossDates();
        expect(operationsService.searchOrders).toHaveBeenLastCalledWith(expect.objectContaining({
            orderId: 42, fromDate: undefined, toDate: undefined, status: undefined, clientFirstName: undefined
        }));
        expect(component.appliedScope()).toContain('All dates');
        expect(component.fromDate()).toBe('');
        expect(component.orderId()).toBe(42);
    });

    it('validates filters before searching and does not replace successful results', () => {
        operationsService.searchOrders.mockReturnValue(of([order(2)]));
        component.search();
        const calls = operationsService.searchOrders.mock.calls.length;
        component.orderId.set(-1);
        component.search();
        expect(operationsService.searchOrders).toHaveBeenCalledTimes(calls);
        expect(component.filterError()).toContain('positive whole');
        component.orderId.set(undefined);
        component.fromDate.set('2026-10-20');
        component.toDate.set('2026-10-01');
        component.search();
        fixture.detectChanges();
        expect(operationsService.searchOrders).toHaveBeenCalledTimes(calls);
        expect(fixture.nativeElement.querySelector('.filter-error')?.textContent).toContain('start date');
        expect(component.orders()[0].orderId).toBe(2);
    });

    it('retains applied results after a failed search and retries the original request despite new draft edits', () => {
        operationsService.searchOrders.mockReturnValueOnce(of([order(2)]));
        component.search();
        const applied = component.appliedScope();
        operationsService.searchOrders.mockReturnValueOnce(throwError(() => ({ status: 503 })));
        component.instrumentSymbol.set(' aapl ');
        component.search();
        component.instrumentSymbol.set('MSFT');
        fixture.detectChanges();
        expect(component.appliedScope()).toBe(applied);
        expect(fixture.nativeElement.querySelector('.blotter-panel').textContent).toContain('#2');
        expect(fixture.nativeElement.querySelector('.draft-notice').textContent).toContain('not applied');
        expect(component.errorMessage()).toContain('retry');
        component.retrySearch();
        expect(operationsService.searchOrders).toHaveBeenLastCalledWith(expect.objectContaining({ instrumentSymbol: 'AAPL' }));
        expect(component.instrumentSymbol()).toBe('MSFT');
        component.discardDraft();
        expect(component.instrumentSymbol()).toBe('AAPL');
    });

    it('navigates through long results and adjacent investigations, then returns focus to the selected row', async () => {
        operationsService.searchOrders.mockReturnValue(of(Array.from({ length: 23 }, (_, i) => order(i + 1))));
        operationsService.inspectOrder.mockImplementation(id => of(audit(id)));
        component.search();
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        const next = [...host.querySelectorAll('app-result-pagination button')].find(button => button.textContent?.trim() === 'Next') as HTMLButtonElement;
        next.click();
        fixture.detectChanges();
        expect(component.visibleOrders()[0].orderId).toBe(11);
        const jump = host.querySelector('[aria-label="Orders page number"]') as HTMLInputElement;
        jump.value = '999'; jump.dispatchEvent(new Event('change', { bubbles: true }));
        fixture.detectChanges();
        expect(component.visibleOrders().length).toBe(3);
        expect(jump.value).toBe('3');
        jump.value = ''; jump.dispatchEvent(new Event('change', { bubbles: true }));
        fixture.detectChanges();
        expect(jump.value).toBe('3');
        jump.value = '2'; jump.dispatchEvent(new Event('change', { bubbles: true }));
        fixture.detectChanges();
        (host.querySelector('.inspect-button') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(document.activeElement?.id).toBe('investigation-panel');
        component.inspectAdjacent(-1);
        fixture.detectChanges();
        expect(component.selectedOrder()?.orderId).toBe(10);
        expect(component.page()).toBe(1);
        component.changePage(3);
        fixture.detectChanges();
        component.returnToResults();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.page()).toBe(1);
        expect(document.activeElement?.getAttribute('data-order-id')).toBe('10');
        expect(component.orders().length).toBe(23);
        expect(component.selectedOrder()).toBeNull();
    });

    it('keeps the current page for refreshes, clamps it after results shrink, and resets it for a new scope', () => {
        operationsService.searchOrders.mockReturnValue(of(Array.from({ length: 23 }, (_, i) => order(i + 1))));
        component.search();
        component.changePage(3);
        component.refreshApplied();
        expect(component.page()).toBe(3);
        operationsService.searchOrders.mockReturnValue(of([order(1)]));
        component.refreshApplied();
        expect(component.page()).toBe(1);
        operationsService.searchOrders.mockReturnValue(of(Array.from({ length: 23 }, (_, i) => order(i + 1))));
        component.refreshApplied();
        component.changePage(3);
        component.side.set('SELL');
        component.search();
        expect(component.page()).toBe(1);
    });

    it('rejects evidence for a mismatched integrity result, account or instrument', () => {
        const wrongIntegrity = audit(2); wrongIntegrity.integrity.orderId = 99;
        const wrongAccount = audit(2); wrongAccount.evidence.account.accountId = 99;
        const wrongInstrument = audit(2); wrongInstrument.evidence.instrument.instrumentId = 99;
        for (const response of [wrongIntegrity, wrongAccount, wrongInstrument]) {
            operationsService.inspectOrder.mockReturnValue(of(response));
            component.inspect(order(2));
            expect(component.audit()).toBeNull();
            expect(component.detailError()).toContain('does not match');
        }
    });

    it('shows the recorded FX, ledger and historical holdings in their own evidence section', () => {
        const result = audit(2);
        Object.assign(result.evidence.execution!, { fxSourceCurrency: 'USD', fxTargetCurrency: 'GBP', fxRate: 0.8,
            fxRateDate: '2026-10-01', fxProvider: 'RecordedProvider', cashSettlementAmount: 10 });
        result.evidence.cashImpact.transactions = [{ transactionId: 77, amount: -10, balanceAfter: 100,
            createdAt: '2026-10-01T12:00:01', description: 'Trade debit' }];
        operationsService.inspectOrder.mockReturnValue(of(result));
        component.inspect(order(2));
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        (host.querySelector('[aria-controls="settlement-evidence"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        const settlement = host.querySelector('#settlement-evidence') as HTMLElement;
        expect(settlement.hidden).toBe(false);
        expect((host.querySelector('#lifecycle-evidence') as HTMLElement).hidden).toBe(true);
        expect(settlement.textContent).toContain('USD → GBP');
        expect(settlement.textContent).toContain('0.8');
        expect(settlement.textContent).toContain('RecordedProvider');
        expect(settlement.textContent).toContain('Transaction #77');
        expect(settlement.textContent).toContain('GBP');
        expect(settlement.textContent).toContain('not current account totals');
        expect(settlement.textContent).toContain('2026-10-01 12:00:01');
    });

    it('explains pending and inconsistent evidence with user recovery steps, without inventing missing FX', () => {
        const pending = audit(2); pending.pending = true;
        expect(component.investigationGuidance(pending)).toContain('refresh');
        const inconsistent = audit(2);
        inconsistent.integrity = { orderId: 2, valid: false, message: 'Invalid persisted order-status lifecycle' };
        inconsistent.evidence.fullyReconstructable = false;
        operationsService.inspectOrder.mockReturnValue(of(inconsistent));
        component.inspect(order(2));
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        expect(host.querySelector('.integrity-banner')?.textContent).toContain('Lifecycle evidence');
        expect(host.querySelector('.integrity-banner')?.classList.contains('integrity-ok')).toBe(false);
        expect(host.querySelector('#settlement-evidence')?.textContent).toContain('Not recorded');
        expect(host.textContent).not.toContain('backend logs');
        expect(component.timestamp('2026-10-01T12:00:00')).toBe('2026-10-01 12:00:00');
        expect(component.timestamp('2026-10-01T12:00:00+02:00')).toBe('2026-10-01 10:00:00 UTC');
    });

    it('restores the user search and selected order by fetching fresh evidence rather than caching it', () => {
        localStorage.setItem('accessToken', 'test.' + btoa(JSON.stringify({ sub: 'david-goal4' })) + '.signature');
        try {
            operationsService.searchOrders.mockReturnValue(of(Array.from({ length: 23 }, (_, i) => order(i + 1))));
            operationsService.inspectOrder.mockImplementation(id => of(audit(id)));
            component.side.set('BUY');
            component.search();
            component.changePage(2);
            component.inspect(component.visibleOrders()[1]);
            component.clientFirstName.set('Unapplied');
            fixture.destroy();
            fixture = TestBed.createComponent(OperationsComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            expect(component.side()).toBe('BUY');
            expect(component.clientFirstName()).toBe('');
            expect(component.selectedOrder()?.orderId).toBe(12);
            expect(component.page()).toBe(2);
            expect(operationsService.inspectOrder).toHaveBeenLastCalledWith(12);
            const key = Object.keys(sessionStorage).find(key => key.includes('david-goal4'))!;
            expect(sessionStorage.getItem(key)).not.toContain('executionPrice');
        } finally {
            localStorage.removeItem('accessToken');
            sessionStorage.clear();
        }
    });

});
