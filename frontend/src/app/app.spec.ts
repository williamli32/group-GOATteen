import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

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
      label => label.textContent?.trim(),
    );
    expect(renderedLabels).toEqual(labels);
  });

  it('lets an operations user open Reports from the sidebar', async () => {
    signIn('OPERATIONS');
    const fixture = await renderAt('/operations');
    const reportsLink = fixture.nativeElement.querySelector('a[href="/analyst"]') as HTMLAnchorElement;
    expect(reportsLink).toBeTruthy();
    reportsLink.click();
    await fixture.whenStable();
    expect(router.url).toBe('/analyst');
  });

  it.each(['/analyst', '/operations'])('redirects a customer opening %s to Dashboard', async url => {
    signIn('CLIENT');
    await renderAt(url);
    expect(router.url).toBe('/dashboard');
  });

  it('redirects an analyst opening Operations to Dashboard', async () => {
    signIn('ANALYST');
    await renderAt('/operations');
    expect(router.url).toBe('/dashboard');
  });

  it.each(['/login', '/register', '/login?returnUrl=%2Fdashboard', '/register?role=CLIENT'])(
    'keeps the sidebar off the public page %s', async url => {
      signIn('OPERATIONS');
      const fixture = await renderAt(url);
      expect(fixture.nativeElement.querySelector('app-sidebar')).toBeNull();
    },
  );
});
