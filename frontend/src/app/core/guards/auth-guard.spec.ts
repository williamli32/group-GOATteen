import {
  TestBed
} from '@angular/core/testing';

import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter
} from '@angular/router';

import {
  vi
} from 'vitest';

import {
  Auth
} from '../auth/auth';

import {
  authGuard
} from './auth-guard';


describe('authGuard', () => {

  const authMock = {

    isLoggedIn:
      vi.fn()

  };


  beforeEach(() => {

    vi.clearAllMocks();


    TestBed.configureTestingModule({

      providers: [

        provideRouter([]),

        {
          provide: Auth,
          useValue: authMock
        }

      ]

    });

  });


  it(
    'should allow authenticated users',
    () => {

      authMock.isLoggedIn
        .mockReturnValue(true);


      const result =
        TestBed.runInInjectionContext(
          () =>
            authGuard(
              {} as ActivatedRouteSnapshot,
              {} as RouterStateSnapshot
            )
        );


      expect(result).toBe(true);

    }
  );


  it(
    'should redirect unauthenticated users',
    () => {

      authMock.isLoggedIn
        .mockReturnValue(false);


      const router =
        TestBed.inject(Router);


      const result =
        TestBed.runInInjectionContext(
          () =>
            authGuard(
              {} as ActivatedRouteSnapshot,
              {} as RouterStateSnapshot
            )
        );


      expect(
        router.serializeUrl(
          result as UrlTree
        )
      ).toBe('/login');

    }
  );

});