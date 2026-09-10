import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent {
  navItems = [
    {
      label: 'Dashboard',
      icon: '📊',
      route: '/dashboard',
    },
    {
      label: 'Audit Log',
      icon: '📋',
      route: '/audit-log',
    },
    {
      label: 'Reports',
      icon: '📈',
      route: '/reports',
    },
    {
      label: 'Portfolio',
      icon: '💼',
      route: '/clientview',
    },
  ];
}
