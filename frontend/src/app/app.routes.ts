import { Routes } from '@angular/router';
import { AuthComponent } from './auth/auth.component';
import { AuditLogPage } from './features/audit-log/audit-log-page/audit-log-page';
import { ReportsPage } from './features/reports/reports-page/reports-page';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ClientViewComponent } from './client-view/clientview.component';
import { roleGuard } from './guards/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/auth', pathMatch: 'full' },
  { path: 'auth', component: AuthComponent },
  { path: 'dashboard', component: DashboardComponent },
  { 
    path: 'audit-log', 
    component: AuditLogPage,
    canActivate: [roleGuard],
    data: { roles: ['admin'] }
  },
  { 
    path: 'reports', 
    component: ReportsPage,
    canActivate: [roleGuard],
    data: { roles: ['admin'] }
  },
  { path: 'clientview', component: ClientViewComponent },
  { path: '**', redirectTo: '/auth' }
];
