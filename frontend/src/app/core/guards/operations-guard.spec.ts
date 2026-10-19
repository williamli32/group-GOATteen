import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Auth } from '../auth/auth';
import { operationsGuard } from './operations-guard';

describe('operationsGuard', () => {
  const auth = { hasOperationsAccess: vi.fn() };
  beforeEach(() => TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: Auth, useValue: auth }]
  }));
  it('allows an operations user or administrator', () => {
    auth.hasOperationsAccess.mockReturnValue(true);
    expect(TestBed.runInInjectionContext(() => operationsGuard(
      {} as ActivatedRouteSnapshot, {} as RouterStateSnapshot))).toBe(true);
  });
  it('redirects a customer or analyst to the dashboard', () => {
    auth.hasOperationsAccess.mockReturnValue(false);
    const result = TestBed.runInInjectionContext(() => operationsGuard(
      {} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/dashboard');
  });
});
