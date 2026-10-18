import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AccountFundingComponent } from './account-funding.component';
import { AccountResponse, AccountService, AddFundsResponse } from '../core/services/account';

describe('AccountFundingComponent', () => {
  let fixture: ComponentFixture<AccountFundingComponent>;
  let component: AccountFundingComponent;
  const service = { addFunds: vi.fn() };
  const account: AccountResponse = {
    accountId: 10, accountNumber: 'ACC-10', cashBalance: 0, currency: 'USD', firstName: 'Test', lastName: 'User'
  };
  const response: AddFundsResponse = {
    accountId: 10, transactionId: 20, amount: 10000, currency: 'USD', cashBalance: 10000,
    createdAt: '2026-10-18T12:00:00', replayed: false
  };

  beforeEach(async () => {
    sessionStorage.clear();
    service.addFunds.mockReset().mockReturnValue(of(response));
    await TestBed.configureTestingModule({
      imports: [AccountFundingComponent], providers: [{ provide: AccountService, useValue: service }]
    }).compileComponents();
    fixture = TestBed.createComponent(AccountFundingComponent);
    fixture.componentRef.setInput('account', account);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.open();
    fixture.detectChanges();
  });

  afterEach(() => sessionStorage.clear());

  it('adds 10K in the account currency and emits the persisted balance', () => {
    const funded = vi.fn();
    component.funded.subscribe(funded);
    component.amount = '10000';
    component.submit();
    fixture.detectChanges();
    expect(service.addFunds).toHaveBeenCalledWith('10000.00', expect.any(String));
    expect(funded).toHaveBeenCalledWith(response);
    expect(fixture.nativeElement.textContent).toContain('Added USD');
    expect(component.amount).toBe('');
    expect(sessionStorage.getItem('leap_pending_funding_10')).toBeNull();
  });

  it.each(['0', '-1', '1.001', 'NaN', 'Infinity', '1e4'])('rejects invalid amount %s', value => {
    component.amount = value;
    component.submit();
    expect(component.error()).toContain('Enter an amount');
    expect(service.addFunds).not.toHaveBeenCalled();
  });

  it('prevents repeated submissions while a request is in flight', () => {
    const pending = new Subject<AddFundsResponse>();
    service.addFunds.mockReturnValue(pending);
    component.amount = '10000';
    component.submit();
    component.submit();
    fixture.detectChanges();
    expect(service.addFunds).toHaveBeenCalledTimes(1);
    expect(component.funding()).toBe(true);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    pending.next(response); pending.complete();
    expect(component.funding()).toBe(false);
  });

  it('retries an unconfirmed request with the same ID instead of crediting twice', () => {
    service.addFunds.mockReturnValueOnce(throwError(() => ({ status: 0 })))
      .mockReturnValueOnce(of({ ...response, replayed: true }));
    component.amount = '10000';
    component.submit();
    const firstKey = service.addFunds.mock.calls[0][1];
    expect(component.error()).toContain('could not confirm');
    component.submit();
    expect(service.addFunds.mock.calls[1]).toEqual(['10000.00', firstKey]);
    expect(component.success()).toContain('previous USD');
    expect(component.pending()).toBeNull();
  });

  it('restores an unconfirmed request when the panel is reopened', () => {
    const key = crypto.randomUUID();
    sessionStorage.setItem('leap_pending_funding_10', JSON.stringify({ amount: '10000.00', key }));
    component.open();
    expect(component.amount).toBe('10000.00');
    component.submit();
    expect(service.addFunds).toHaveBeenCalledWith('10000.00', key);
  });

  it('requires a previous amount to be confirmed before accepting a different amount', () => {
    const key = crypto.randomUUID();
    sessionStorage.setItem('leap_pending_funding_10', JSON.stringify({ amount: '10000.00', key }));
    component.amount = '20';
    component.submit();
    expect(service.addFunds).not.toHaveBeenCalled();
    expect(component.amount).toBe('10000.00');
    expect(component.error()).toContain('Confirm the previous');
  });

  it('does not reuse a pending request from a different account', () => {
    sessionStorage.setItem('leap_pending_funding_99', JSON.stringify({ amount: '5.00', key: crypto.randomUUID() }));
    component.amount = '10000';
    component.submit();
    expect(service.addFunds).toHaveBeenCalledWith('10000.00', expect.any(String));
  });

  it('uses whole units for a JPY account', () => {
    fixture.componentRef.setInput('account', { ...account, currency: 'JPY' });
    fixture.detectChanges();
    component.amount = '10.50';
    component.submit();
    expect(service.addFunds).not.toHaveBeenCalled();
    component.amount = '10000';
    component.submit();
    expect(service.addFunds).toHaveBeenCalledWith('10000', expect.any(String));
  });

  it('lets a user correct an amount rejected before funding was applied', () => {
    service.addFunds.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'Balance limit exceeded' } })));
    component.amount = '10000';
    component.submit();
    expect(component.pending()).toBeNull();
    expect(component.funding()).toBe(false);
    expect(component.error()).toBe('Balance limit exceeded');
  });
});
