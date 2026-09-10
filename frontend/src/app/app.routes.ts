import { Routes } from '@angular/router';
import { AuditLogPage } from './features/audit-log/audit-log-page/audit-log-page';
import { ReportsPage } from './features/reports/reports-page/reports-page';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ClientViewComponent } from './client-view/clientview.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'audit-log', component: AuditLogPage },
  { path: 'reports', component: ReportsPage },
  { path: 'clientview', component: ClientViewComponent },
];
