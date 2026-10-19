import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from '../auth/auth';

export const operationsGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  return auth.hasOperationsAccess() ? true : inject(Router).createUrlTree(['/dashboard']);
};
