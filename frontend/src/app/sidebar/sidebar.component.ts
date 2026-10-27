import { ThemeToggleComponent } from '../shared/theme-toggle.component';
import { Component, computed, HostListener, OnDestroy, OnInit, signal } from '@angular/core';
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
  host: { '[style.--sidebar-width.px]': 'sidebarWidth()' },
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    MotionToggleComponent,
    ThemeToggleComponent,
  ],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnInit, OnDestroy {
  readonly sidebarWidth = signal(240);
  readonly userName = signal('User');
  private resizeStart: { pointerX: number; width: number } | null = null;
  private previousBodyCursor = '';
  private previousBodyUserSelect = '';
  private readonly minSidebarWidth = 200;
  private readonly maxSidebarWidth = 340;
  private readonly defaultSidebarWidth = 240;

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
  ) {
    try {
      const savedWidth = Number(localStorage.getItem('leap_sidebar_width'));
      if (Number.isFinite(savedWidth) && savedWidth >= this.minSidebarWidth && savedWidth <= this.maxSidebarWidth) {
        this.sidebarWidth.set(savedWidth);
      }
    } catch {
      // Storage may be unavailable; the default width remains usable.
    }
  }

  startResize(event: PointerEvent): void {
    if (event.button !== 0 || window.matchMedia('(max-width: 850px)').matches) return;
    event.preventDefault();
    this.resizeStart = { pointerX: event.clientX, width: this.sidebarWidth() };
    this.previousBodyCursor = document.body.style.cursor;
    this.previousBodyUserSelect = document.body.style.userSelect;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }

  @HostListener('document:pointermove', ['$event'])
  onResizePointerMove(event: PointerEvent): void {
    if (!this.resizeStart) return;
    this.sidebarWidth.set(this.clampSidebarWidth(this.resizeStart.width + event.clientX - this.resizeStart.pointerX));
  }

  @HostListener('document:pointerup')
  finishResize(): void {
    if (!this.resizeStart) return;
    this.resizeStart = null;
    document.body.style.cursor = this.previousBodyCursor;
    document.body.style.userSelect = this.previousBodyUserSelect;
    this.saveSidebarWidth();
  }

  resizeFromKeyboard(event: KeyboardEvent): void {
    const step = event.shiftKey ? 24 : 8;
    let width = this.sidebarWidth();
    if (event.key === 'ArrowLeft') width -= step;
    else if (event.key === 'ArrowRight') width += step;
    else if (event.key === 'Home') width = this.minSidebarWidth;
    else if (event.key === 'End') width = this.maxSidebarWidth;
    else if (event.key === 'Enter') width = this.defaultSidebarWidth;
    else return;

    event.preventDefault();
    this.sidebarWidth.set(this.clampSidebarWidth(width));
    this.saveSidebarWidth();
  }

  private clampSidebarWidth(width: number): number {
    return Math.min(this.maxSidebarWidth, Math.max(this.minSidebarWidth, width));
  }

  private saveSidebarWidth(): void {
    try {
      localStorage.setItem('leap_sidebar_width', String(this.sidebarWidth()));
    } catch {
      // Keep the in-memory width if storage is unavailable.
    }
  }

  ngOnDestroy(): void {
    if (this.resizeStart) {
      document.body.style.cursor = this.previousBodyCursor;
      document.body.style.userSelect = this.previousBodyUserSelect;
      this.resizeStart = null;
    }
  }

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
