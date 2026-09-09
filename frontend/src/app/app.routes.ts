import { Routes } from '@angular/router';
import { AuthComponent } from './auth/auth.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ClientViewComponent } from './client-view/clientview.component';

export const routes: Routes = [

  { path: '', component: AuthComponent },
  { path: 'auth', component: AuthComponent },
  { path: 'dashboard', component: DashboardComponent }
  { path: 'clientview', component: ClientViewComponent }
];
