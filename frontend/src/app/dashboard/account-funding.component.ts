import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AccountResponse, AccountService, AddFundsResponse } from '../core/services/account';

interface PendingFunding { amount: string; key: string; }
type MoneyAction = 'fund' | 'withdraw';

@Component({
  selector: 'app-account-funding', standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './account-funding.component.html',
  styleUrl: './account-funding.component.scss'
})
export class AccountFundingComponent {
  readonly account = input.required<AccountResponse>();
  readonly funded = output<AddFundsResponse>();
  readonly isOpen = signal(false);
  readonly funding = signal(false);
  readonly pending = signal<PendingFunding | null>(null);
  readonly error = signal('');
  readonly success = signal('');
  readonly action = signal<MoneyAction>('fund');
  readonly confirmWithdrawal = signal(false);
  amount = '';
  private readonly service = inject(AccountService);
  private readonly destroyRef = inject(DestroyRef);

  open(): void {
    this.error.set('');
    this.success.set('');
    this.isOpen.set(true);
    try {
      const previous = this.readPending(this.storageKey());
      this.pending.set(previous);
      this.amount = previous?.amount ?? '';
    } catch {
      this.error.set('Your pending funding request could not be read. Please reload and retry.');
    }
  }

  selectAction(action: MoneyAction): void {
    this.action.set(action);
    this.error.set('');
    this.success.set('');
    this.amount = '';
  }

  minorUnits(): number {
    return new Intl.NumberFormat('en-GB', { style: 'currency', currency: this.account().currency })
      .resolvedOptions().maximumFractionDigits ?? 2;
  }

  submit(): void {
    if (this.action() === 'withdraw') {
      this.confirmWithdrawal.set(true);
      return;
    }
    this.submitFunding();
  }

  cancelWithdrawal(): void {
    if (!this.funding()) this.confirmWithdrawal.set(false);
  }

  confirmWithdrawalRequest(): void {
    this.confirmWithdrawal.set(false);
    this.submitFunding();
  }

  private submitFunding(): void {
    if (this.funding()) return;
    this.error.set('');
    this.success.set('');
    const account = this.account();
    const digits = this.minorUnits();
    const value = this.amount.trim();
    const pattern = digits === 0 ? /^\d{1,16}$/ : new RegExp('^\\d{1,16}(?:\\.\\d{1,' + digits + '})?$');
    if (!pattern.test(value) || BigInt(value.replace('.', '')) <= 0n) {
      this.error.set(`Enter an amount greater than zero in ${account.currency}, with up to ${digits} decimal places.`);
      return;
    }
    const [whole, fraction = ''] = value.split('.');
    const amount = BigInt(whole).toString() + (digits ? '.' + fraction.padEnd(digits, '0') : '');
    const storageKey = this.storageKey();
    let request: PendingFunding;
    try {
      const previous = this.readPending(storageKey);
      if (previous && previous.amount !== amount) {
        this.pending.set(previous);
        this.amount = previous.amount;
        this.error.set('Confirm the previous funding request before adding another amount.');
        return;
      }
      request = previous ?? { amount, key: crypto.randomUUID() };
      // Keep the same request ID across network errors and page reloads.
      sessionStorage.setItem(storageKey, JSON.stringify(request));
    } catch {
      this.error.set('Enable browser session storage so this funding request can be confirmed safely.');
      return;
    }
    this.pending.set(request);
    this.funding.set(true);
    const request$ = this.action() === 'withdraw'
      ? this.service.withdrawFunds(request.amount, request.key)
      : this.service.addFunds(request.amount, request.key);
    request$.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.funding.set(false))
    ).subscribe({
      next: response => {
        sessionStorage.removeItem(storageKey);
        this.pending.set(null);
        this.amount = '';
        const money = new Intl.NumberFormat('en-GB', { style: 'currency', currency: response.currency,
          currencyDisplay: 'code' }).format(response.amount);
        const verb = this.action() === 'withdraw' ? 'Withdrew' : 'Added';
        this.success.set(response.replayed ? `Your previous ${money} request is confirmed.` : `${verb} ${money} ${this.action() === 'withdraw' ? 'from' : 'to'} your account.`);
        this.funded.emit(response);
      },
      error: failure => {
        if (failure.status === 400) {
          sessionStorage.removeItem(storageKey);
          this.pending.set(null);
          this.error.set(failure.error?.message && failure.error.message !== 'Validation failed'
            ? failure.error.message : 'Check the amount and try again.');
        } else {
          this.error.set('We could not confirm this request. Retry to check it before adding more funds.');
        }
      }
    });
  }

  private storageKey(): string {
    return `leap_pending_${this.action()}_${this.account().accountId}`;
  }

  private readPending(key: string): PendingFunding | null {
    const stored = sessionStorage.getItem(key);
    if (!stored) return null;
    const value = JSON.parse(stored);
    if (typeof value.amount !== 'string' || typeof value.key !== 'string'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.key)) {
      throw new Error('Invalid pending funding request');
    }
    return value;
  }
}
