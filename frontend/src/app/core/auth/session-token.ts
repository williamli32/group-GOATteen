import { signal } from '@angular/core';

// Per-tab memory only. The backend verifies JWTs and role permissions.
export const accessToken = signal<string | null>(null);

export function tokenClaims(token: string | null): { sub?: unknown; roles?: unknown } {
  try {
    if (!token) return {};
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const claims: unknown = JSON.parse(atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, '=')));
    return claims !== null && typeof claims === 'object' && !Array.isArray(claims) ? claims : {};
  } catch { return {}; }
}
