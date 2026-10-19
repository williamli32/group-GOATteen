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
});
