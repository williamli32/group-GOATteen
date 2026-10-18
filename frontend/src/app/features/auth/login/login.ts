import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { Auth } from '../../../core/auth/auth';


@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    RouterLink
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login {


  email = '';
  password = '';

  readonly errorMessage = signal('');

  readonly loading = signal(false);
  showPassword = false;


  constructor(
    private auth: Auth,
    private router: Router
  ) {}


  login(event: Event) {

    event.preventDefault();
    if (this.loading()) return;
    this.loading.set(true);
    this.errorMessage.set('');

    this.auth.login({
      email: this.email,
      password: this.password
    })
    .pipe(finalize(() => this.loading.set(false)))
    .subscribe({

      next: response => {

        this.auth.saveToken(
          response.accessToken
        );


        this.router.navigate([
          this.auth.hasAnalystAccess() ? '/analyst' : '/dashboard'
        ]);

      },


      error: () => {

        this.errorMessage.set('Invalid email or password');

      }

    });

  }

}
