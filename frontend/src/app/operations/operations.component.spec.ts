import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OperationsComponent } from './operations.component';
import { OperationsService } from '../core/services/operations';

describe('OperationsComponent', () => {
    let component: OperationsComponent;
    let fixture: ComponentFixture<OperationsComponent>;
    let operationsService: {
        searchOrders: ReturnType<typeof vi.fn>;
        inspectOrder: ReturnType<typeof vi.fn>;
    };

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
});
