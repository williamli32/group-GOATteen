import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth-guard';

import { analystGuard } from './core/guards/analyst-guard';

import { operationsGuard } from './core/guards/operations-guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((module) => module.Login),
  },

  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register').then((module) => module.Register),
  },

  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/dashboard.component').then((module) => module.DashboardComponent),
    canActivate: [authGuard],
  },

  {
    path: 'analyst',
    loadComponent: () =>
      import('./analyst/analyst-dashboard.component').then(
        (module) => module.AnalystDashboardComponent,
      ),
    canActivate: [authGuard, analystGuard],
  },

  {
    path: 'operations',
    loadComponent: () =>
      import('./operations/operations.component').then((module) => module.OperationsComponent),
    canActivate: [authGuard, operationsGuard],
  },

  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },

  {
    path: '**',
    redirectTo: 'login',
  },
];
