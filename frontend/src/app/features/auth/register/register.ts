import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { Auth } from '../../../core/auth/auth';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class Register {

  firstName = '';
  lastName = '';
  email = '';
  password = '';
  confirmPassword = '';

  showPassword = false;
  showConfirmPassword = false;

  loading = signal(false);
  errorMessage = signal('');
  validationMessage = signal('');

  constructor(
    private auth: Auth,
    private router: Router
  ) {}

  register(): void {

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

    if (this.password !== this.confirmPassword) {
      this.validationMessage.set(
        'Passwords do not match.'
      );
      return;
    }

    this.loading.set(true);

    this.auth.register({
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      email: this.email.trim(),
      password: this.password
    })
    .pipe(
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
            'Please check your information and try again.'
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