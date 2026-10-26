import { Injectable, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, catchError, map, of, switchMap, tap, throwError, timeout } from 'rxjs';
import { accessToken, tokenClaims } from './session-token';

export interface RefreshResponse { accessToken: string; }
export interface LoginRequest { email: string; password: string; }
export interface LoginResponse { accessToken: string; }
export type RegistrationRole = 'CLIENT' | 'ANALYST' | 'OPERATIONS';
export interface AccountCurrencyOption { code: string; name: string; minorUnits: number; }
interface CsrfResponse { headerName: string; token: string; }

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly apiUrl = `${environment.apiUrl}/auth`;
  private revision = 0;
  private readonly roles = computed(() => {
    const roles = tokenClaims(accessToken()).roles;
    return Array.isArray(roles) ? roles.map(role => String(role).toUpperCase()) : [];
  });

  constructor(private readonly http: HttpClient) {
    // Discard tokens persisted by older versions; restore through the HttpOnly cookie.
    try { localStorage.removeItem('accessToken'); sessionStorage.removeItem('accessToken'); } catch { }
  }

  login(request: LoginRequest): Observable<LoginResponse> { return this.authPost('login', request); }
  saveToken(token: string): void { this.revision++; accessToken.set(token); }
  getToken(): string | null { return accessToken(); }
  clearToken(): void { this.revision++; accessToken.set(null); }
  isLoggedIn(): boolean { return !!accessToken(); }
  hasAnalystAccess(): boolean { return this.roles().some(role => role === 'ANALYST' || role === 'ADMIN'); }
  hasReportingAccess(): boolean {
    return this.roles().some(role => ['ANALYST', 'OPERATIONS', 'ADMIN'].includes(role));
  }
  hasAdminAccess(): boolean { return this.roles().includes('ADMIN'); }
  hasOperationsAccess(): boolean {
    return this.roles().some(role => role === 'OPERATIONS' || role === 'ADMIN');
  }
  getAccountCurrencies(): Observable<AccountCurrencyOption[]> {
    return this.http.get<AccountCurrencyOption[]>(`${this.apiUrl}/account-currencies`);
  }
  register(data: {
    firstName: string; lastName: string; email: string; password: string;
    role: RegistrationRole; currency: string; accessCode?: string;
  }): Observable<unknown> { return this.authPost('register', data); }

  refresh(): Observable<RefreshResponse> {
    const revision = this.revision;
    return this.authPost<RefreshResponse>('refresh', {}).pipe(
      tap(response => {
        if (revision !== this.revision) throw new Error('The session has changed.');
        this.saveToken(response.accessToken);
      }),
      catchError(error => {
        // An old refresh must not clear a subsequent login or restore a logged-out session.
        if (revision === this.revision) this.clearToken();
        return throwError(() => error);
      }),
    );
  }
  restoreSession(): Observable<boolean> {
    return this.refresh().pipe(map(() => true), catchError(() => of(false)));
  }
  logoutSession(): Observable<void> {
    this.clearToken();
    return this.authPost<void>('logout', {});
  }

  private authPost<T>(path: string, body: unknown): Observable<T> {
    // The masked CSRF token comes from a credentialed API request. Its cookie stays HttpOnly.
    return this.http.get<CsrfResponse>(`${this.apiUrl}/csrf`, { withCredentials: true }).pipe(
      switchMap(csrf => this.http.post<T>(`${this.apiUrl}/${path}`, body, {
        withCredentials: true, headers: { [csrf.headerName]: csrf.token },
      })),
      timeout(10_000),
    );
  }
}
