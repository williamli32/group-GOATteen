import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  Router,
  provideRouter,
  NavigationStart,
  NavigationCancel,
  NavigationEnd,
  NavigationError,
} from '@angular/router';
import { of, Subject } from 'rxjs';
import { vi } from 'vitest';

import { App } from './app';
import { Auth } from './core/auth/auth';
import { analystGuard } from './core/guards/analyst-guard';
import { operationsGuard } from './core/guards/operations-guard';
import { AccountService } from './core/services/account';

@Component({ template: '<p>Page</p>' })
class TestPage {}

describe('App', () => {
  let auth: Auth;
  let router: Router;

  beforeEach(async () => {
    localStorage.removeItem('accessToken');
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideRouter([
          { path: 'dashboard', component: TestPage },
          { path: 'analyst', component: TestPage, canActivate: [analystGuard] },
          { path: 'operations', component: TestPage, canActivate: [operationsGuard] },
          { path: 'login', component: TestPage },
          { path: 'register', component: TestPage },
        ]),
        {
          provide: AccountService,
          useValue: {
            getMyAccount: () => of({ firstName: 'David', lastName: 'Smith' }),
          },
        },
      ],
    }).compileComponents();
    auth = TestBed.inject(Auth);
    router = TestBed.inject(Router);
  });

  afterEach(() => auth.clearToken());

  function signIn(role: string): void {
    auth.saveToken('header.' + btoa(JSON.stringify({ roles: [role] })) + '.signature');
  }

  async function renderAt(url: string) {
    await router.navigateByUrl(url);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('renders the router outlet without a sidebar when signed out', async () => {
    const fixture = await renderAt('/login');
    expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-sidebar')).toBeNull();
  });

  it.each([
    { role: 'CLIENT', labels: ['Dashboard'] },
    { role: 'ANALYST', labels: ['Dashboard', 'Reports'] },
    { role: 'OPERATIONS', labels: ['Dashboard', 'Reports', 'Operations'] },
    { role: 'ADMIN', labels: ['Dashboard', 'Reports', 'Operations'] },
  ])('shows the correct sidebar sections for $role', async ({ role, labels }) => {
    signIn(role);
    const fixture = await renderAt('/dashboard');
    const renderedLabels = Array.from(
      fixture.nativeElement.querySelectorAll('.nav-label') as NodeListOf<HTMLElement>,
      (label) => label.textContent?.trim(),
    );
    expect(renderedLabels).toEqual(labels);
  });

  it('lets an operations user open Reports from the sidebar', async () => {
    signIn('OPERATIONS');
    const fixture = await renderAt('/operations');
    const reportsLink = fixture.nativeElement.querySelector(
      'a[href="/analyst"]',
    ) as HTMLAnchorElement;
    expect(reportsLink).toBeTruthy();
    reportsLink.click();
    await fixture.whenStable();
    expect(router.url).toBe('/analyst');
  });

  it('completes the customer journey with Dashboard as the only workspace route', async () => {
    signIn('CLIENT');
    const fixture = await renderAt('/dashboard');
    const links = Array.from(fixture.nativeElement.querySelectorAll('a.nav-item'),
      (link: HTMLAnchorElement) => link.getAttribute('href'));

    expect(router.url).toBe('/dashboard');
    expect(links).toEqual(['/dashboard']);
    fixture.nativeElement.querySelector('a[href="/dashboard"]').click();
    await fixture.whenStable();
    expect(router.url).toBe('/dashboard');
  });

  it('completes the analyst journey between Dashboard and Reports only', async () => {
    signIn('ANALYST');
    const fixture = await renderAt('/dashboard');
    const links = Array.from(fixture.nativeElement.querySelectorAll('a.nav-item'),
      (link: HTMLAnchorElement) => link.getAttribute('href'));
    expect(links).toEqual(['/dashboard', '/analyst']);

    fixture.nativeElement.querySelector('a[href="/analyst"]').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toBe('/analyst');
    fixture.nativeElement.querySelector('a[href="/dashboard"]').click();
    await fixture.whenStable();
    expect(router.url).toBe('/dashboard');
  });

  it('completes the operations journey across Dashboard, Reports, and Operations', async () => {
    signIn('OPERATIONS');
    const fixture = await renderAt('/dashboard');
    const links = Array.from(fixture.nativeElement.querySelectorAll('a.nav-item'),
      (link: HTMLAnchorElement) => link.getAttribute('href'));
    expect(links).toEqual(['/dashboard', '/analyst', '/operations']);

    for (const path of ['/analyst', '/operations', '/dashboard']) {
      fixture.nativeElement.querySelector(`a[href="${path}"]`).click();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(router.url).toBe(path);
    }
  });

  it.each(['/analyst', '/operations'])(
    'redirects a customer opening %s to Dashboard',
    async (url) => {
      signIn('CLIENT');
      await renderAt(url);
      expect(router.url).toBe('/dashboard');
    },
  );

  it('redirects an analyst opening Operations to Dashboard', async () => {
    signIn('ANALYST');
    await renderAt('/operations');
    expect(router.url).toBe('/dashboard');
  });

  it.each(['/login', '/register', '/login?returnUrl=%2Fdashboard', '/register?role=CLIENT'])(
    'keeps the sidebar off the public page %s',
    async (url) => {
      signIn('OPERATIONS');
      const fixture = await renderAt(url);
      expect(fixture.nativeElement.querySelector('app-sidebar')).toBeNull();
    },
  );
  it('shows page-loading feedback and clears it after navigation or cancellation', () => {
    const events = new Subject<NavigationStart | NavigationEnd | NavigationCancel>();
    vi.spyOn(router, 'events', 'get').mockReturnValue(events);
    const fixture = TestBed.createComponent(App);
    events.next(new NavigationStart(1, '/login'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Opening page"]')).not.toBeNull();
    events.next(new NavigationEnd(1, '/login', '/login'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Opening page"]')).toBeNull();
    events.next(new NavigationStart(2, '/register'));
    events.next(new NavigationCancel(2, '/register', 'Cancelled'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Opening page"]')).toBeNull();
  });

  it('lets the user retry a failed page download without exposing technical errors', () => {
    const events = new Subject<NavigationStart | NavigationError>();
    vi.spyOn(router, 'events', 'get').mockReturnValue(events);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(App);
    events.next(new NavigationError(1, '/analyst', new Error('Chunk download failed')));
    fixture.detectChanges();
    const banner = fixture.nativeElement.querySelector('.route-error') as HTMLElement;
    expect(banner.textContent).toContain('Check your connection');
    expect(banner.textContent).not.toContain('Chunk');
    banner.querySelector('button')!.click();
    expect(navigate).toHaveBeenCalledWith('/analyst');
    events.next(new NavigationStart(2, '/analyst'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.route-error')).toBeNull();
  });
});
