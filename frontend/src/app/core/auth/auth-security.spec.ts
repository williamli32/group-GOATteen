import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Auth } from './auth';
import { accessToken } from './session-token';
import { jwtInterceptor } from '../interceptors/jwt-interceptor';
import { environment } from '../../../environments/environment';
import { readWorkflowPreferences, saveWorkflowPreferences } from '../utils/reporting-workflow';

const api = environment.apiUrl;
const jwt = (sub: string) => 'header.' + btoa(JSON.stringify({ sub, roles: ['CLIENT'] })) + '.signature';

describe('Session security', () => {
  let auth: Auth;
  let http: HttpClient;
  let requests: HttpTestingController;
  beforeEach(() => {
    accessToken.set(null);
    localStorage.setItem('accessToken', 'obsolete-persisted-token');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([jwtInterceptor])), provideHttpClientTesting()],
    });
    auth = TestBed.inject(Auth);
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { requests.verify(); auth.clearToken(); localStorage.clear(); sessionStorage.clear(); });

  function csrf() {
    const request = requests.expectOne(api + '/auth/csrf');
    expect(request.request.withCredentials).toBe(true);
    request.flush({ headerName: 'X-XSRF-TOKEN', token: 'masked-csrf' });
  }
  function pendingRefresh() {
    csrf();
    const request = requests.expectOne(api + '/auth/refresh');
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('masked-csrf');
    return request;
  }

  it('discards legacy stored tokens and scopes preferences using only the memory token', () => {
    expect(auth.getToken()).toBeNull();
    expect(localStorage.getItem('accessToken')).toBeNull();
    auth.saveToken(jwt('first'));
    expect(auth.getToken()).toBe(jwt('first'));
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('accessToken')).toBeNull();
    saveWorkflowPreferences('reports', { currency: 'INR' });
    auth.saveToken(jwt('second'));
    expect(readWorkflowPreferences('reports')).toBeNull();
    auth.saveToken(jwt('first'));
    expect(readWorkflowPreferences('reports')).toEqual({ currency: 'INR' });
  });

  it('restores a page reload through a CSRF-protected refresh without persisting a JWT', () => {
    let restored: boolean | undefined;
    auth.restoreSession().subscribe(value => restored = value);
    pendingRefresh().flush({ accessToken: jwt('first') });
    expect(restored).toBe(true);
    expect(auth.isLoggedIn()).toBe(true);
    expect(localStorage.getItem('accessToken')).toBeNull();
  });

  it('finishes startup signed out when no refresh session exists', () => {
    let restored: boolean | undefined;
    auth.restoreSession().subscribe(value => restored = value);
    pendingRefresh().flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(restored).toBe(false);
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('never sends a bearer token or credentials to a different origin or API-lookalike path', () => {
    auth.saveToken(jwt('first'));
    for (const url of ['https://untrusted.example/api/orders', api + '-other/accounts']) {
      http.get(url).subscribe();
      const request = requests.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      expect(request.request.withCredentials).toBe(false);
      request.flush({});
    }
  });

  it('shares one refresh when two protected requests receive 401 together', () => {
    auth.saveToken(jwt('old'));
    const urls = [api + '/accounts/me', api + '/market/instruments'];
    for (const url of urls) http.get(url).subscribe();
    for (const url of urls) requests.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });
    pendingRefresh().flush({ accessToken: jwt('new') });
    for (const url of urls) {
      const request = requests.expectOne(url);
      expect(request.request.headers.get('Authorization')).toBe('Bearer ' + jwt('new'));
      request.flush({});
    }
  });

  it('does not let an in-flight refresh restore a session after logout', () => {
    auth.saveToken(jwt('first'));
    let failed = false;
    auth.refresh().subscribe({ error: () => failed = true });
    const refresh = pendingRefresh();
    auth.logoutSession().subscribe();
    csrf();
    requests.expectOne(api + '/auth/logout').flush(null);
    refresh.flush({ accessToken: jwt('old-refresh') });
    expect(failed).toBe(true);
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('does not let an old failed refresh clear a newer login', () => {
    auth.refresh().subscribe({ error: () => {} });
    const refresh = pendingRefresh();
    auth.saveToken(jwt('new-login'));
    refresh.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.getToken()).toBe(jwt('new-login'));
  });
});
