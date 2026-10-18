import {
  ComponentFixture,
  TestBed
} from '@angular/core/testing';

import {
  provideHttpClient
} from '@angular/common/http';

import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import {
  provideRouter
} from '@angular/router';

import { Login } from './login';
import { environment } from '../../../../environments/environment';


describe('Login', () => {

  let component: Login;
  let fixture: ComponentFixture<Login>;


  beforeEach(async () => {

    await TestBed.configureTestingModule({

      imports: [
        Login
      ],

      providers: [

        provideHttpClient(),
        provideHttpClientTesting(),

        provideRouter([])

      ]

    }).compileComponents();


    fixture =
      TestBed.createComponent(Login);

    component =
      fixture.componentInstance;

    fixture.detectChanges();

  });


  it('should create', () => {

    expect(component).toBeTruthy();

  });

  it('disables repeated submissions and clears loading after a failed login', () => {
    const http = TestBed.inject(HttpTestingController);
    component.email = 'analyst@example.com';
    component.password = 'invalid-password';
    component.errorMessage.set('Previous error');
    component.login(new Event('submit'));
    component.login(new Event('submit'));
    fixture.detectChanges();
    expect(component.loading()).toBe(true);
    expect(component.errorMessage()).toBe('');
    expect(fixture.nativeElement.querySelector('.submit-button').disabled).toBe(true);
    const requests = http.match(environment.apiUrl + '/auth/login');
    expect(requests.length).toBe(1);
    requests[0].flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(component.loading()).toBe(false);
    expect(fixture.nativeElement.querySelector('.submit-button').disabled).toBe(false);
    expect(component.errorMessage()).toContain('Invalid email or password');
    http.verify();
  });
});
