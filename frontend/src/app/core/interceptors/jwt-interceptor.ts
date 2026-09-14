import { inject } from '@angular/core';
import {
  HttpErrorResponse,
  HttpInterceptorFn
} from '@angular/common/http';
import {
  catchError,
  switchMap,
  throwError
} from 'rxjs';

import { Auth } from '../auth/auth';


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

      /*
       * Do not try refreshing if:
       * - the failure wasn't 401
       * - the request itself is an auth endpoint
       */
      if (
        error.status !== 401 ||
        isAuthRequest
      ) {
        return throwError(() => error);
      }


      return auth.refresh().pipe(

        catchError(refreshError => {

          auth.clearToken();

          return throwError(
            () => refreshError
          );

        }),

        switchMap(refreshResponse => {

          auth.saveToken(
            refreshResponse.accessToken
          );

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