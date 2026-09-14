import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

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

  errorMessage = '';

  loading = false;
  showPassword = false;


  constructor(
    private auth: Auth,
    private router: Router
  ) {}


  login(event: Event) {

    event.preventDefault();


    this.auth.login({
      email: this.email,
      password: this.password
    })
    .subscribe({

      next: response => {

        this.auth.saveToken(
          response.accessToken
        );


        this.router.navigate([
          '/dashboard'
        ]);

      },


      error: () => {

        this.errorMessage =
          'Invalid email or password';

      }

    });

  }

}