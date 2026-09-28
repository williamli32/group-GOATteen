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

  /*
   * Market-data-service is a public, read-only service
   * for historical CSV data. Don't include JWT credentials
   * or withCredentials flag.
   */
  const isMarketDataServiceRequest =
    req.url.includes('localhost:8081') ||
    req.url.includes(':8081') ||
    req.url.includes('/market-data');


  let request = req;

  /*
   * Only set withCredentials for trading-platform API.
   * Market-data-service is public and doesn't use cookies.
   */
  if (!isMarketDataServiceRequest) {

    request = request.clone({
      withCredentials: true
    });

  }

  /*
   * Add JWT only to trading-platform API requests.
   * Market-data-service is public and doesn't require auth.
   */
  if (
    token &&
    !isAuthRequest &&
    !isMarketDataServiceRequest
  ) {

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