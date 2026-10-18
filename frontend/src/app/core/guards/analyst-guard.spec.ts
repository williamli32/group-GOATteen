import { TestBed } from '@angular/core/testing';
import {
    ActivatedRouteSnapshot,
    Router,
    RouterStateSnapshot,
    UrlTree,
    provideRouter
} from '@angular/router';
import { vi } from 'vitest';
import { Auth } from '../auth/auth';
import { analystGuard } from './analyst-guard';

describe('analystGuard', () => {
    const authMock = {
        hasReportingAccess: vi.fn()
    };

    beforeEach(() => {
        vi.clearAllMocks();
        TestBed.configureTestingModule({
            providers: [
                provideRouter([]),
                { provide: Auth, useValue: authMock }
            ]
        });
    });

    it('allows users with Reports access', () => {
        authMock.hasReportingAccess.mockReturnValue(true);

        const result = TestBed.runInInjectionContext(() => analystGuard(
            {} as ActivatedRouteSnapshot,
            {} as RouterStateSnapshot
        ));

        expect(result).toBe(true);
    });

    it('redirects regular users to the dashboard', () => {
        authMock.hasReportingAccess.mockReturnValue(false);
        const router = TestBed.inject(Router);

        const result = TestBed.runInInjectionContext(() => analystGuard(
            {} as ActivatedRouteSnapshot,
            {} as RouterStateSnapshot
        ));

        expect(router.serializeUrl(result as UrlTree)).toBe('/dashboard');
    });
});
