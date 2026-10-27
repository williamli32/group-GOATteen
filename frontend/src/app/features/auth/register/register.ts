import { ThemeToggleComponent } from '../../../shared/theme-toggle.component';
import { MarketMotionComponent } from '../../../shared/market-motion.component';
import { MotionToggleComponent } from '../../../shared/motion-toggle.component';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AccountCurrencyOption, Auth, RegistrationRole } from '../../../core/auth/auth';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink, MarketMotionComponent, MotionToggleComponent, ThemeToggleComponent],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class Register implements OnInit {

  firstName = '';
  lastName = '';
  email = '';
  password = '';
  confirmPassword = '';
  role: RegistrationRole = 'CLIENT';
  currency = '';
  accessCode = '';
  readonly currencyOptions = signal<AccountCurrencyOption[]>([]);
  readonly currenciesLoading = signal(false);
  readonly currenciesError = signal('');
  private readonly destroyRef = inject(DestroyRef);

  showPassword = false;
  showConfirmPassword = false;
  showAccessCode = false;

  loading = signal(false);
  errorMessage = signal('');
  validationMessage = signal('');

  constructor(
    private auth: Auth,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.loadCurrencies();
  }

  loadCurrencies(): void {
    if (this.currenciesLoading()) return;
    this.currenciesLoading.set(true);
    this.currenciesError.set('');
    this.auth.getAccountCurrencies().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.currenciesLoading.set(false))
    ).subscribe({
      next: currencies => this.currencyOptions.set(currencies),
      error: () => this.currenciesError.set('Account currencies could not be loaded. Please retry.')
    });
  }

  register(): void {

    if (this.loading()) return;

    this.errorMessage.set('');
    this.validationMessage.set('');

    if (
      !this.firstName.trim() ||
      !this.lastName.trim() ||
      !this.email.trim() ||
      !this.password ||
      !this.confirmPassword
    ) {
      this.validationMessage.set(
        'Please complete all required fields.'
      );
      return;
    }

    if (
      this.password.length < 12 ||
      this.password.length > 72
    ) {
      this.validationMessage.set(
        'Password must be between 12 and 72 characters.'
      );
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.validationMessage.set(
        'Passwords do not match.'
      );
      return;
    }

    if (this.role !== 'CLIENT' && !this.accessCode.trim()) {
      this.validationMessage.set(
        this.role === 'ANALYST' ? 'An analyst access code is required.' : 'An operations access code is required.'
      );
      return;
    }

    if (!this.currencyOptions().some(option => option.code === this.currency)) {
      this.validationMessage.set('Please select an account currency.');
      return;
    }

    this.loading.set(true);

    this.auth.register({
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      email: this.email.trim(),
      password: this.password,
      role: this.role,
      currency: this.currency,
      accessCode: this.role !== 'CLIENT' ? this.accessCode : undefined
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading.set(false);
        })
      )
      .subscribe({

        next: () => {
          this.router.navigate(['/login']);
        },

        error: (error) => {

          if (error.status === 409) {
            this.errorMessage.set(
              'An account with this email already exists.'
            );

          } else if (error.status === 400) {
            this.errorMessage.set(
              error.error?.message && error.error.message !== 'Validation failed'
                ? error.error.message : 'Please check your information and try again.'
            );

          } else {
            this.errorMessage.set(
              'Registration failed. Please try again.'
            );
          }
        }

      });
  }
}
