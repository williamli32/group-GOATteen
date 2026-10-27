import { ThemePreferences } from './core/ui/theme-preferences';
import { ThemeToggleComponent } from './shared/theme-toggle.component';
import { MotionToggleComponent } from './shared/motion-toggle.component';
import { Component, computed, inject, signal } from '@angular/core';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
  RouterOutlet,
} from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';

import { Auth } from './core/auth/auth';
import { SidebarComponent } from './sidebar/sidebar.component';

@Component({
  imports: [RouterOutlet, SidebarComponent, MotionToggleComponent, ThemeToggleComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  private readonly auth = inject(Auth);

  readonly openingPage = signal(false);
  readonly pageError = signal(false);
  private failedUrl: string | null = null;

  constructor() {
    inject(ThemePreferences);
    this.router.events.pipe(takeUntilDestroyed()).subscribe((event) => {
      if (event instanceof NavigationStart) {
        this.openingPage.set(true);
        this.pageError.set(false);
      } else if (event instanceof NavigationError) {
        this.openingPage.set(false);
        this.pageError.set(true);
        this.failedUrl = event.url;
      } else if (event instanceof NavigationEnd || event instanceof NavigationCancel) {
        this.openingPage.set(false);
      }
    });
  }

  retryPage(): void {
    if (this.failedUrl) void this.router.navigateByUrl(this.failedUrl).catch(() => undefined);
  }

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly showSidebar = computed(() => {
    const url = this.currentUrl().split(/[?#]/)[0];
    const isPublicRoute = url === '/login' || url === '/register';

    return this.auth.isLoggedIn() && !isPublicRoute;
  });
}
