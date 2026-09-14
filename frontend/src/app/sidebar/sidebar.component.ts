import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles?: string[];
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
      roles: ['client'],
    },
    {
      label: 'Portfolio',
      icon: '💼',
      route: '/clientview',
      roles: ['client'],
    },
    {
      label: 'Reports',
      icon: '📈',
      route: '/reports',
      roles: ['admin'],
    },
    {
      label: 'Audit Log',
      icon: '📋',
      route: '/audit-log',
      roles: ['admin'],
    },
  ];

  // Computed signal to filter nav items based on user role
  navItems = computed(() => {
    const userRole = this.authService.getRole();
    if (!userRole) return [];
    return this.allNavItems.filter(
      (item) => !item.roles || item.roles.includes(userRole)
    );
  });

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth']);
  }
}
