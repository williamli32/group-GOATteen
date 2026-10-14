import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';


export interface RefreshResponse {
  accessToken: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}


export interface LoginResponse {
  accessToken: string;
}


@Injectable({
  providedIn: 'root'
})
export class Auth {

  private apiUrl = `${environment.apiUrl}/auth`;
  private readonly roles = signal<string[]>([]);

  constructor(
    private http: HttpClient
  ) {
    this.roles.set(this.readRoles(this.getToken()));
  }


  login(
    request: LoginRequest
  ): Observable<LoginResponse> {

    return this.http.post<LoginResponse>(
      `${this.apiUrl}/login`,
      request,
      {
        withCredentials: true
      }
    );
  }


  saveToken(token: string): void {
    localStorage.setItem(
      'accessToken',
      token
    );
    this.roles.set(this.readRoles(token));
  }


  getToken(): string | null {
    return localStorage.getItem(
      'accessToken'
    );
  }


  clearToken(): void {
    localStorage.removeItem(
      'accessToken'
    );
    this.roles.set([]);
  }

  hasAnalystAccess(): boolean {
    return this.roles().some(role => role === 'ANALYST' || role === 'ADMIN');
  }


  isLoggedIn(): boolean {
    return !!this.getToken();
  }


  register(data: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    role: 'CLIENT' | 'ANALYST';
    accessCode?: string;
  }) {
    return this.http.post(
      `${this.apiUrl}/register`,
      data,
      {
        withCredentials: true
      }
    );
  }

  refresh(): Observable<RefreshResponse> {

    return this.http.post<RefreshResponse>(
      `${this.apiUrl}/refresh`,
      {},
      {
        withCredentials: true
      }
    );
  }


  logoutSession(): Observable<void> {

    return this.http.post<void>(
      `${this.apiUrl}/logout`,
      {},
      {
        withCredentials: true
      }
    );
  }

  private readRoles(token: string | null): string[] {
    if (!token) {
      return [];
    }

    try {
      const payload = token.split('.')[1];
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const claims = JSON.parse(atob(normalized));
      return Array.isArray(claims.roles)
        ? claims.roles.map((role: unknown) => String(role).toUpperCase())
        : [];
    } catch {
      return [];
    }
  }

}