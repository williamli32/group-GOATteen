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

import {
  analystGuard
} from './core/guards/analyst-guard';

import {
  AnalystDashboardComponent
} from './analyst/analyst-dashboard.component';

import {
  OperationsComponent
} from './operations/operations.component';


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
    path: 'analyst',
    component: AnalystDashboardComponent,
    canActivate: [
      authGuard,
      analystGuard
    ]
  },

  {
    path: 'operations',
    component: OperationsComponent,
    canActivate: [
      authGuard,
      analystGuard
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