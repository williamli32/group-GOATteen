import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Auth } from '../auth/auth';
import { adminGuard } from './admin-guard';

describe('adminGuard', () => {
    const authMock = { hasAdminAccess: vi.fn() };

    beforeEach(() => {
        vi.clearAllMocks();
        TestBed.configureTestingModule({
            providers: [provideRouter([]), { provide: Auth, useValue: authMock }]
        });
    });

    it('allows administrators', () => {
        authMock.hasAdminAccess.mockReturnValue(true);

        const result = TestBed.runInInjectionContext(() => adminGuard(
            {} as ActivatedRouteSnapshot,
            {} as RouterStateSnapshot
        ));

        expect(result).toBe(true);
    });

    it('redirects analysts and clients to the dashboard', () => {
        authMock.hasAdminAccess.mockReturnValue(false);
        const router = TestBed.inject(Router);

        const result = TestBed.runInInjectionContext(() => adminGuard(
            {} as ActivatedRouteSnapshot,
            {} as RouterStateSnapshot
        ));

        expect(router.serializeUrl(result as UrlTree)).toBe('/dashboard');
    });
});
