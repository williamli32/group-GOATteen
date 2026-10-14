import { Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';

import { Auth } from './core/auth/auth';
import { SidebarComponent } from './sidebar/sidebar.component';

@Component({
  imports: [RouterOutlet, SidebarComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  private readonly auth = inject(Auth);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly showSidebar = computed(() => {
    const url = this.currentUrl();
    const isPublicRoute = url === '/login' || url === '/register';

    return this.auth.isLoggedIn()
      && this.auth.hasAnalystAccess()
      && !isPublicRoute;
  });
}
