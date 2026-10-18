import { TestBed } from '@angular/core/testing';
import { Auth } from './auth';
import { provideHttpClient } from '@angular/common/http';

describe('Auth', () => {
  let service: Auth;

  beforeEach(() => {
    localStorage.removeItem('accessToken');
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
    service = TestBed.inject(Auth);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
  afterEach(() => service.clearToken());

  function token(roles: string[]) { return 'header.' + btoa(JSON.stringify({ roles })) + '.signature'; }

  it('gives operations its own access without analyst or administrator privileges', () => {
    service.saveToken(token(['OPERATIONS']));
    expect(service.hasOperationsAccess()).toBe(true);
    expect(service.hasAnalystAccess()).toBe(false);
    expect(service.hasAdminAccess()).toBe(false);
  });

  it.each(['CLIENT', 'ANALYST'])('does not give operations access to %s', role => {
    service.saveToken(token([role]));
    expect(service.hasOperationsAccess()).toBe(false);
  });

  it('retains operations access for administrators', () => {
    service.saveToken(token(['ADMIN']));
    expect(service.hasOperationsAccess()).toBe(true);
    expect(service.hasAnalystAccess()).toBe(true);
  });
});
