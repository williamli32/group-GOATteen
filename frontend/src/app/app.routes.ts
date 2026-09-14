import {
  Routes
} from '@angular/router';

import {
  Login
} from './features/auth/login/login';

import {
  Register
} from './features/auth/register/register';

import {
  DashboardComponent
} from './dashboard/dashboard.component';

import {
  authGuard
} from './core/guards/auth-guard';


export const routes: Routes = [

  {
    path: 'login',
    component: Login
  },

  {
    path: 'register',
    component: Register
  },

  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [
      authGuard
    ]
  },

  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },

  {
    path: '**',
    redirectTo: 'login'
  }

];