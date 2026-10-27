import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AccountFundingComponent } from './account-funding.component';
import { AccountResponse, AccountService, AddFundsResponse } from '../core/services/account';

describe('AccountFundingComponent', () => {
  let fixture: ComponentFixture<AccountFundingComponent>;
  let component: AccountFundingComponent;
  const service = { addFunds: vi.fn(), withdrawFunds: vi.fn() };
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
    service.withdrawFunds.mockReset().mockReturnValue(of({ ...response, amount: -100, cashBalance: 900 }));
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
    expect(sessionStorage.getItem('leap_pending_fund_10')).toBeNull();
  });

  it('keeps the funding amount labelled and the submit action named', () => {
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('label[for="funding-amount"]')?.textContent).toContain('Amount');
    expect(root.querySelector<HTMLInputElement>('#funding-amount')).not.toBeNull();
    expect(root.querySelector<HTMLButtonElement>('button[type="submit"]')?.textContent).toContain('Add USD');
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
    sessionStorage.setItem('leap_pending_fund_10', JSON.stringify({ amount: '10000.00', key }));
    component.open();
    expect(component.amount).toBe('10000.00');
    component.submit();
    expect(service.addFunds).toHaveBeenCalledWith('10000.00', key);
  });

  it('requires a previous amount to be confirmed before accepting a different amount', () => {
    const key = crypto.randomUUID();
    sessionStorage.setItem('leap_pending_fund_10', JSON.stringify({ amount: '10000.00', key }));
    component.amount = '20';
    component.submit();
    expect(service.addFunds).not.toHaveBeenCalled();
    expect(component.amount).toBe('10000.00');
    expect(component.error()).toContain('Confirm the previous');
  });

  it('does not reuse a pending request from a different account', () => {
    sessionStorage.setItem('leap_pending_fund_99', JSON.stringify({ amount: '5.00', key: crypto.randomUUID() }));
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

  it('locks money-action changes while a deposit is in flight and reports the original operation', () => {
    const request = new Subject<AddFundsResponse>(); service.addFunds.mockReturnValue(request);
    component.amount = '10000'; component.submit(); component.selectAction('withdraw'); fixture.detectChanges();
    expect(component.action()).toBe('fund');
    expect(Array.from(fixture.nativeElement.querySelectorAll('.money-tabs button') as NodeListOf<HTMLButtonElement>).every(button => button.disabled)).toBe(true);
    request.next(response); request.complete();
    expect(component.success()).toContain('Added'); expect(service.withdrawFunds).not.toHaveBeenCalled();
  });

  it('keeps unconfirmed deposits and withdrawals separate and restores the pending amount on return', () => {
    service.addFunds.mockReturnValue(throwError(() => ({ status: 0 })));
    component.amount = '10000'; component.submit();
    const key = component.pending()!.key;
    component.selectAction('withdraw'); fixture.detectChanges();
    expect(component.pending()).toBeNull();
    expect((fixture.nativeElement.querySelector('#funding-amount') as HTMLInputElement).readOnly).toBe(false);
    component.selectAction('fund');
    expect(component.amount).toBe('10000.00'); expect(component.pending()!.key).toBe(key);
  });

  it('confirms withdrawals with focus trapping, Escape and focus restoration', () => {
    component.selectAction('withdraw'); component.amount = '100'; fixture.detectChanges();
    const trigger = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    trigger.focus(); component.submit(); fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[role="alertdialog"]') as HTMLElement;
    const [cancel, confirm] = Array.from(dialog.querySelectorAll('button'));
    expect(document.activeElement).toBe(cancel);
    expect(fixture.nativeElement.querySelector('form').hasAttribute('inert')).toBe(true);
    confirm.focus(); confirm.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(cancel);
    cancel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(confirm);
    confirm.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);
    expect(fixture.nativeElement.querySelector('form').hasAttribute('inert')).toBe(false);
    expect(service.withdrawFunds).not.toHaveBeenCalled();
  });

  it('allows correction after insufficient funds and formats a successful withdrawal as a positive amount', () => {
    component.selectAction('withdraw'); component.amount = '100';
    service.withdrawFunds.mockReturnValueOnce(throwError(() => ({ status: 409, error: { message: 'Insufficient available cash' } })))
      .mockReturnValueOnce(of({ ...response, amount: -50, cashBalance: 950 }));
    component.submit(); fixture.detectChanges(); component.confirmWithdrawalRequest(); fixture.detectChanges();
    expect(component.error()).toBe('Insufficient available cash'); expect(component.pending()).toBeNull();
    expect((fixture.nativeElement.querySelector('#funding-amount') as HTMLInputElement).readOnly).toBe(false);
    component.amount = '50'; component.submit(); fixture.detectChanges(); component.confirmWithdrawalRequest();
    expect(component.success()).toContain('Withdrew USD'); expect(component.success()).not.toContain('-');
    expect(service.withdrawFunds).toHaveBeenLastCalledWith('50.00', expect.any(String));
  });

  it('rejects an invalid withdrawal before opening a confirmation dialog', () => {
    component.selectAction('withdraw'); component.amount = '0'; component.submit(); fixture.detectChanges();
    expect(component.confirmWithdrawal()).toBe(false); expect(component.error()).toContain('greater than zero');
    expect(service.withdrawFunds).not.toHaveBeenCalled();
  });
});
