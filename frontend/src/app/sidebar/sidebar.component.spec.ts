import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { Auth } from '../core/auth/auth';
import { AccountService } from '../core/services/account';
import { SidebarComponent } from './sidebar.component';

describe('SidebarComponent role navigation', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  const auth = {
    hasReportingAccess: vi.fn(),
    hasOperationsAccess: vi.fn(),
    logoutSession: vi.fn(() => of(void 0)),
    clearToken: vi.fn()
  };
  const account = {
    getMyAccount: vi.fn(() => of({ firstName: 'Test', lastName: 'User' }))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    auth.hasReportingAccess.mockReturnValue(false);
    auth.hasOperationsAccess.mockReturnValue(false);
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        { provide: Auth, useValue: auth },
        { provide: AccountService, useValue: account }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
  });

  it('shows customers only the Dashboard route', () => {
  expect(fixture.componentInstance.navItems().map(item => item.route))
    .toEqual(['/dashboard']);
});

  it('shows analysts Dashboard and Reports routes', () => {
    auth.hasReportingAccess.mockReturnValue(true);
    fixture.destroy();
    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.navItems().map(item => item.route))
      .toEqual(['/dashboard', '/analyst']);
  });

  it('shows operations Dashboard, Reports, and Operations routes', () => {
    auth.hasReportingAccess.mockReturnValue(true);
    auth.hasOperationsAccess.mockReturnValue(true);
    fixture.destroy();
    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.navItems().map(item => item.route))
      .toEqual(['/dashboard', '/analyst', '/operations']);
  });
});
