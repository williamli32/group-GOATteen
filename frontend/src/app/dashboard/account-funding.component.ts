import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AccountResponse, AccountService, AddFundsResponse } from '../core/services/account';
import { ModalFocusDirective } from '../shared/modal-focus.directive';

interface PendingFunding {
  amount: string;
  key: string;
}
type MoneyAction = 'fund' | 'withdraw';

@Component({
  selector: 'app-account-funding',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalFocusDirective],
  templateUrl: './account-funding.component.html',
  styleUrl: './account-funding.component.scss',
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
  readonly withdrawalAmount = signal('');
  amount = '';
  private readonly drafts: Record<MoneyAction, string> = { fund: '', withdraw: '' };
  private readonly service = inject(AccountService);
  private readonly destroyRef = inject(DestroyRef);

  open(): void {
    this.error.set('');
    this.success.set('');
    this.isOpen.set(true);
    this.restoreAction();
  }

  selectAction(action: MoneyAction): void {
    if (this.funding() || this.confirmWithdrawal() || this.action() === action) return;
    this.drafts[this.action()] = this.amount;
    this.action.set(action);
    this.error.set('');
    this.success.set('');
    this.restoreAction();
  }

  private restoreAction(): void {
    try {
      const previous = this.readPending(this.storageKey(this.action(), this.account().accountId));
      this.pending.set(previous);
      this.amount = previous?.amount ?? this.drafts[this.action()];
    } catch {
      this.pending.set(null);
      this.error.set(
        'Your previous money request could not be read. Reload this page before trying again.',
      );
    }
  }

  minorUnits(): number {
    return (
      new Intl.NumberFormat('en-GB', {
        style: 'currency',
        currency: this.account().currency,
      }).resolvedOptions().maximumFractionDigits ?? 2
    );
  }

  submit(): void {
    if (this.funding() || this.confirmWithdrawal()) return;
    this.error.set('');
    this.success.set('');
    const amount = this.normalizedAmount();
    if (amount === null) return;
    if (this.action() === 'withdraw') {
      this.withdrawalAmount.set(amount);
      this.confirmWithdrawal.set(true);
    } else this.submitFunding('fund', amount);
  }

  cancelWithdrawal(): void {
    if (!this.funding()) this.confirmWithdrawal.set(false);
  }

  confirmWithdrawalRequest(): void {
    if (!this.confirmWithdrawal() || this.funding()) return;
    const amount = this.withdrawalAmount();
    this.confirmWithdrawal.set(false);
    this.submitFunding('withdraw', amount);
  }

  private normalizedAmount(): string | null {
    const currency = this.account().currency;
    const digits = this.minorUnits();
    const value = this.amount.trim();
    const pattern =
      digits === 0 ? /^\d{1,16}$/ : new RegExp('^\\d{1,16}(?:\\.\\d{1,' + digits + '})?$');
    if (!pattern.test(value) || BigInt(value.replace('.', '')) <= 0n) {
      this.error.set(
        `Enter an amount greater than zero in ${currency}, with up to ${digits} decimal places.`,
      );
      return null;
    }
    const [whole, fraction = ''] = value.split('.');
    return BigInt(whole).toString() + (digits ? '.' + fraction.padEnd(digits, '0') : '');
  }

  private submitFunding(action: MoneyAction, amount: string): void {
    if (this.funding()) return;
    const accountId = this.account().accountId;
    const storageKey = this.storageKey(action, accountId);
    let request: PendingFunding;
    try {
      const previous = this.readPending(storageKey);
      if (previous && previous.amount !== amount) {
        this.pending.set(previous);
        this.amount = previous.amount;
        this.error.set('Confirm the previous request before entering another amount.');
        return;
      }
      request = previous ?? { amount, key: crypto.randomUUID() };
      sessionStorage.setItem(storageKey, JSON.stringify(request));
    } catch {
      this.error.set('Enable browser session storage so this request can be confirmed safely.');
      return;
    }
    this.pending.set(request);
    this.funding.set(true);
    const request$ =
      action === 'withdraw'
        ? this.service.withdrawFunds(request.amount, request.key)
        : this.service.addFunds(request.amount, request.key);
    request$
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.funding.set(false)),
      )
      .subscribe({
        next: (response) => {
          this.clearStored(storageKey);
          this.pending.set(null);
          this.amount = '';
          this.drafts[action] = '';
          const money = new Intl.NumberFormat('en-GB', {
            style: 'currency',
            currency: response.currency,
            currencyDisplay: 'code',
          }).format(Math.abs(response.amount));
          this.success.set(
            response.replayed
              ? `Your previous ${money} request is confirmed.`
              : action === 'withdraw'
                ? `Withdrew ${money} from your account.`
                : `Added ${money} to your account.`,
          );
          this.funded.emit(response);
        },
        error: (failure) => {
          // A definitive rejection permits correction; network/5xx failures retain the original key.
          if ([400, 401, 403, 409, 422].includes(failure.status)) {
            this.clearStored(storageKey);
            this.pending.set(null);
            this.error.set(
              failure.status === 401
                ? 'Your session expired. Sign in again before retrying.'
                : failure.error?.message && failure.error.message !== 'Validation failed'
                  ? failure.error.message
                  : 'This request was rejected. Check the amount and try again.',
            );
          } else {
            this.amount = request.amount;
            this.error.set(
              'We could not confirm this request. Retry with the same amount to confirm its outcome.',
            );
          }
        },
      });
  }

  private clearStored(key: string): void {
    try {
      sessionStorage.removeItem(key);
    } catch {
      /* A confirmed response remains authoritative. */
    }
  }

  private storageKey(action: MoneyAction, accountId: number): string {
    return `leap_pending_${action}_${accountId}`;
  }

  private readPending(key: string): PendingFunding | null {
    const stored = sessionStorage.getItem(key);
    if (!stored) return null;
    const value = JSON.parse(stored);
    if (
      typeof value.amount !== 'string' ||
      typeof value.key !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.key)
    )
      throw new Error('Invalid pending request');
    return value;
  }
}
