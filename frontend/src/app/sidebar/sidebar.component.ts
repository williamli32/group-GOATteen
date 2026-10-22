import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Auth } from '../core/auth/auth';
import { AccountService } from '../core/services/account';
import { MotionToggleComponent } from '../shared/motion-toggle.component';

interface NavItem {
  label: string;
  route: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, MotionToggleComponent],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnInit {
  readonly userName = signal('User');

  private allNavItems: NavItem[] = [
    {
      label: 'Dashboard',
      route: '/dashboard',
    },
    {
      label: 'Reports',
      route: '/analyst',
    },
    {
      label: 'Operations',
      route: '/operations',
    },
  ];

  navItems = computed(() =>
    this.allNavItems.filter((item) =>
      item.route === '/operations'
        ? this.auth.hasOperationsAccess()
        : item.route !== '/analyst' || this.auth.hasReportingAccess(),
    ),
  );

  constructor(
    private auth: Auth,
    private router: Router,
    private accountService: AccountService,
  ) {}

  ngOnInit(): void {
    this.accountService.getMyAccount().subscribe({
      next: (account) => {
        const name = `${account.firstName} ${account.lastName}`.trim();
        this.userName.set(name || 'User');
      },
    });
  }

  userInitial(): string {
    return this.userName().charAt(0).toUpperCase();
  }

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
