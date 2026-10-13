import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Auth } from '../core/auth/auth';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent {
  private allNavItems: NavItem[] = [
    {
      label: 'Dashboard',
      icon: '📊',
      route: '/dashboard',
    },
    {
      label: 'Reports',
      icon: '📈',
      route: '/analyst',
    },
  ];

  navItems = computed(() => this.allNavItems);

  constructor(
    private auth: Auth,
    private router: Router
  ) {}

  logout(): void {
    this.auth.logoutSession().subscribe({
      next: () => this.finishLogout(),
      error: () => this.finishLogout(),
    });
  }

  private finishLogout(): void {
    this.auth.clearToken();
    this.router.navigate(['/login']);
  }
}
