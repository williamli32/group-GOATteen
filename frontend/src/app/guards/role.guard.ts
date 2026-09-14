import { inject } from '@angular/core';
import { Router, CanActivateFn, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService, UserRole } from '../services/auth.service';

/**
 * MOCK ROLE-BASED ACCESS GUARD - Frontend UI demonstration only
 * Protects routes based on user role using route data configuration.
 * No backend authorization checks. Frontend control only.
 */
export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Check if user is logged in
  if (!authService.isLoggedIn()) {
    router.navigate(['/auth']);
    return false;
  }

  // Get required roles from route data
  const requiredRoles = route.data['roles'] as UserRole[] | undefined;

  // If no roles specified, allow access
  if (!requiredRoles || requiredRoles.length === 0) {
    return true;
  }

  // Check if user has required role
  const userRole = authService.getRole();
  if (userRole && requiredRoles.includes(userRole)) {
    return true;
  }

  // User doesn't have required role, redirect to dashboard
  router.navigate(['/dashboard']);
  return false;
};
