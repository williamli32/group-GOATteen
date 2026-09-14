import { inject } from '@angular/core';
import {
  HttpErrorResponse,
  HttpInterceptorFn
} from '@angular/common/http';

import {
  Observable,
  catchError,
  finalize,
  shareReplay,
  switchMap,
  tap,
  throwError
} from 'rxjs';

import {
  Auth,
  RefreshResponse
} from '../auth/auth';


let refreshRequest$: Observable<RefreshResponse> | null = null;


export const jwtInterceptor: HttpInterceptorFn = (req, next) => {

  const auth = inject(Auth);

  const token = auth.getToken();

  const isAuthRequest =
    req.url.includes('/api/auth/');


  let request = req.clone({
    withCredentials: true
  });


  if (token && !isAuthRequest) {

    request = request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });

  }


  return next(request).pipe(

    catchError((error: HttpErrorResponse) => {

      if (
        error.status !== 401 ||
        isAuthRequest
      ) {
        return throwError(() => error);
      }


      /*
       * Multiple API calls can fail with 401 at the same time.
       *
       * Only allow ONE refresh-token rotation.
       * Other failed requests share the same refresh request.
       */
      if (!refreshRequest$) {

        refreshRequest$ = auth.refresh().pipe(

          tap(refreshResponse => {

            auth.saveToken(
              refreshResponse.accessToken
            );

          }),

          catchError(refreshError => {

            auth.clearToken();

            return throwError(
              () => refreshError
            );

          }),

          finalize(() => {

            refreshRequest$ = null;

          }),

          shareReplay(1)

        );

      }


      return refreshRequest$.pipe(

        switchMap(refreshResponse => {

          const retriedRequest =
            req.clone({
              withCredentials: true,
              setHeaders: {
                Authorization:
                  `Bearer ${refreshResponse.accessToken}`
              }
            });

          return next(retriedRequest);

        })

      );

    })

  );

};