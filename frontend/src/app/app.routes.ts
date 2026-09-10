import { Routes } from '@angular/router';
import { AuditLogPage } from './features/audit-log/audit-log-page/audit-log-page';
import { ReportsPage } from './features/reports/reports-page/reports-page';

export const routes: Routes = [
  {
    path: 'audit-log',
    component: AuditLogPage,
  },
  {
    path: 'reports',
    component: ReportsPage,
  },
];