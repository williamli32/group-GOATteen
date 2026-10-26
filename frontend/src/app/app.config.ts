import { ApplicationConfig, inject, provideAppInitializer } from '@angular/core';
import { Auth } from './core/auth/auth';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideAppInitializer(() => inject(Auth).restoreSession()),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([
        jwtInterceptor
      ])
    )
  ]
};
