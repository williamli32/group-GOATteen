import { Injectable } from '@angular/core';
import { signal } from '@angular/core';

export type UserRole = 'client' | 'admin';

export interface MockUser {
  role: UserRole;
  email: string;
}

/**
 * MOCK AUTHENTICATION SERVICE - Frontend UI demonstration only
 * This is NOT production authentication. No backend, JWTs, or real security.
 * Used only to control sidebar visibility and route access for UI demo purposes.
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private currentUser = signal<MockUser | null>(this.loadUser());

  constructor() {}

  /**
   * Mock login with role selection
   * @param role - 'client' or 'admin'
   * @param email - Email for display purposes
   */
  login(role: UserRole, email: string): void {
    const user: MockUser = { role, email };
    this.currentUser.set(user);
    sessionStorage.setItem('mockUser', JSON.stringify(user));
  }

  /**
   * Logout and clear mock user
   */
  logout(): void {
    this.currentUser.set(null);
    sessionStorage.removeItem('mockUser');
  }

  /**
   * Check if user is logged in
   */
  isLoggedIn(): boolean {
    return this.currentUser() !== null;
  }

  /**
   * Get current user role
   */
  getRole(): UserRole | null {
    const user = this.currentUser();
    return user?.role ?? null;
  }

  /**
   * Check if current user is admin
   */
  isAdmin(): boolean {
    return this.getRole() === 'admin';
  }

  /**
   * Get current user (for display purposes)
   */
  getCurrentUser(): MockUser | null {
    return this.currentUser();
  }

  /**
   * Load user from sessionStorage on app init
   */
  private loadUser(): MockUser | null {
    try {
      const stored = sessionStorage.getItem('mockUser');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  }
}
